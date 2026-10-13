package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical sale-item→recipe links, locking the current source fact so concurrent
 * corrections cannot leave two current versions.
 */
@Service
class CanonicalSaleRecipeLinkService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalSaleRecipeLinkRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalSaleRecipeLinkService(
      CanonicalSaleRecipeLinkRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalSaleRecipeLink record(SaleRecipeLinkInput input) {
    CanonicalSaleRecipeLink saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new SaleRecipeLinkRecorded(input.recipeId()));
    return saved;
  }

  /**
   * Record a batch of facts in one transaction, publishing a single event so the projector rebuilds
   * once instead of once per fact.
   */
  @Transactional
  void recordAll(List<SaleRecipeLinkInput> inputs) {
    for (SaleRecipeLinkInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
    if (!inputs.isEmpty()) {
      publisher.publishEvent(new SaleRecipeLinkRecorded(inputs.get(0).recipeId()));
    }
  }

  @Transactional
  CanonicalSaleRecipeLink recordAt(SaleRecipeLinkInput input, Instant recordedAt) {
    String ref = "sale-recipe-link:" + input.saleItemRecipeId();
    UUID logicalId = logicalIdFor(input.saleItemRecipeId());
    Optional<CanonicalSaleRecipeLink> current =
        repository.lockCurrentSaleRecipeLink(ref, input.sourceSystem());

    // The resolved Lightspeed product id is a derived enrichment, not a CTB source fact. A CTB
    // re-pull passes null, so a successor must carry the existing row's id forward unless the
    // input explicitly sets one — otherwise the periodic re-pull would wipe the resolved id.
    Integer carriedProductId = input.lightspeedProductId();
    if (current.isPresent()) {
      CanonicalSaleRecipeLink existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      if (carriedProductId == null) {
        carriedProductId = existing.lightspeedProductId();
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalSaleRecipeLink.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.saleItemRecipeId(),
            input.saleItemStockCode(),
            input.saleItemStockDescription(),
            input.recipeId(),
            input.recipeName(),
            input.recipeFolderName(),
            input.isRecipeActive(),
            carriedProductId));
  }

  /**
   * Set (or clear) the resolved Lightspeed product id on a link. The id is a derived enrichment set
   * by the matcher, not a CTB source fact, so this supersedes only the id while copying every CTB
   * fact field and the logical identity/raw provenance from the current row. Idempotent: a call
   * with the id already in place, or a missing current row, is a no-op.
   */
  @Transactional
  void setProductId(int saleItemRecipeId, Integer productId) {
    Optional<CanonicalSaleRecipeLink> current =
        repository.lockCurrentBySaleItemRecipeId(saleItemRecipeId);
    if (current.isEmpty()) {
      return;
    }
    CanonicalSaleRecipeLink existing = current.get();
    if (Objects.equals(existing.lightspeedProductId(), productId)) {
      return;
    }

    Instant now = CLOCK.instant();
    existing.supersede(now);
    repository.saveAndFlush(existing);

    repository.save(
        CanonicalSaleRecipeLink.create(
            existing.logicalEntityId(),
            existing.sourceSystem(),
            existing.sourceRecordRef(),
            existing.rawRecordId(),
            now,
            now,
            existing.saleItemRecipeId(),
            existing.saleItemStockCode(),
            existing.saleItemStockDescription(),
            existing.recipeId(),
            existing.recipeName(),
            existing.recipeFolderName(),
            existing.isRecipeActive(),
            productId));
    publisher.publishEvent(new SaleRecipeLinkRecorded(existing.recipeId()));
  }

  private static UUID logicalIdFor(int saleItemRecipeId) {
    return UUID.nameUUIDFromBytes(
        ("sale-recipe-link:" + saleItemRecipeId).getBytes(StandardCharsets.UTF_8));
  }
}
