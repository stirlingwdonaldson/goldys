package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the source-product → business-product mapping as a reviewable, bitemporal fact. A mapping
 * is superseded when it changes (a re-classification, or a human accepting/rejecting an automatic
 * suggestion), so alias history is retained rather than overwritten.
 */
@Service
class ProductMappingService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final ProductMappingRepository repository;

  ProductMappingService(ProductMappingRepository repository) {
    this.repository = repository;
  }

  @Transactional
  void map(ProductMappingInput input) {
    mapAt(input, CLOCK.instant());
  }

  @Transactional
  void mapAt(ProductMappingInput input, Instant recordedAt) {
    UUID logicalId =
        UUID.nameUUIDFromBytes(
            ("product-mapping:" + input.sourceSystem() + ":" + input.sourceRecordRef())
                .getBytes(StandardCharsets.UTF_8));
    Optional<ProductMapping> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), input.sourceRecordRef());

    if (current.isPresent()) {
      ProductMapping existing = current.get();
      if (existing.sameFact(input)) {
        return;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    repository.save(
        ProductMapping.create(
            logicalId,
            input.sourceSystem(),
            input.sourceRecordRef(),
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.productId(),
            input.confidence(),
            input.reviewStatus()));
  }

  /** True when the hub already carries an accepted mapping from a different source system. */
  @Transactional(readOnly = true)
  boolean hasCrossSourceMapping(UUID productId, String sourceSystem) {
    return repository.currentAcceptedForProduct(productId).stream()
        .anyMatch(m -> !m.sourceSystem().equals(sourceSystem));
  }
}
