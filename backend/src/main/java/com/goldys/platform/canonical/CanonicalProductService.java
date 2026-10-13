package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical Lightspeed product facts, locking the current source fact so concurrent
 * corrections cannot leave two current versions. The logical identity is a deterministic UUID of
 * the Lightspeed product id, so a rename (same product id) supersedes in place rather than forking.
 */
@Service
class CanonicalProductService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalProductRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalProductService(
      CanonicalProductRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  /**
   * Record a batch of facts in one transaction, publishing a single event so the projector rebuilds
   * once instead of once per fact (the projector's rebuild is whole-table, not per-product).
   */
  @Transactional
  void recordAll(List<ProductInput> inputs) {
    for (ProductInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
    if (!inputs.isEmpty()) {
      publisher.publishEvent(new ProductRecorded(inputs.get(0).productId()));
    }
  }

  /**
   * Snapshot versioning: close every current product whose id is absent from {@code
   * presentProductIds}, so "current" always equals the latest snapshot and history is retained
   * rather than deleted. Publishes a single {@link ProductRecorded} when anything was closed.
   */
  @Transactional
  void closeAbsent(Set<Integer> presentProductIds) {
    List<CanonicalProduct> current = repository.lockAllCurrent();
    CanonicalProduct firstSuperseded = null;
    for (CanonicalProduct product : current) {
      if (!presentProductIds.contains(product.productId())) {
        product.supersede(CLOCK.instant());
        repository.saveAndFlush(product);
        if (firstSuperseded == null) {
          firstSuperseded = product;
        }
      }
    }
    if (firstSuperseded != null) {
      publisher.publishEvent(new ProductRecorded(firstSuperseded.productId()));
    }
  }

  @Transactional
  CanonicalProduct recordAt(ProductInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.productId());
    String ref = "lightspeed-product:" + input.productId();
    Optional<CanonicalProduct> current = repository.lockCurrentProduct(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalProduct existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalProduct.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.productId(),
            input.productName(),
            input.category(),
            input.sellPriceIncTax(),
            input.isModifier(),
            input.reportingGroup()));
  }

  private static UUID logicalIdFor(int productId) {
    return UUID.nameUUIDFromBytes(
        ("lightspeed-product:" + productId).getBytes(StandardCharsets.UTF_8));
  }
}
