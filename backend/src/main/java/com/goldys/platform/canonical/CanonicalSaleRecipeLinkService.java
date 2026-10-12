package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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

  @Transactional
  CanonicalSaleRecipeLink recordAt(SaleRecipeLinkInput input, Instant recordedAt) {
    String ref = "sale-recipe-link:" + input.saleItemRecipeId();
    UUID logicalId = logicalIdFor(input.saleItemRecipeId());
    Optional<CanonicalSaleRecipeLink> current =
        repository.lockCurrentSaleRecipeLink(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalSaleRecipeLink existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
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
            input.isRecipeActive()));
  }

  private static UUID logicalIdFor(int saleItemRecipeId) {
    return UUID.nameUUIDFromBytes(
        ("sale-recipe-link:" + saleItemRecipeId).getBytes(StandardCharsets.UTF_8));
  }
}
