package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a source product to a stable logical identity. The identity is the source's own product
 * code; the name is an attribute that may change. Two products with different codes stay distinct
 * even when their names match.
 */
@Service
class SourceProductService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final SourceProductRepository repository;

  SourceProductService(SourceProductRepository repository) {
    this.repository = repository;
  }

  @Transactional
  SourceProductIdentity record(SourceProductInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  SourceProductIdentity recordAt(SourceProductInput input, Instant recordedAt) {
    String sourceRef = input.sourceRecordRef();
    UUID logicalId =
        UUID.nameUUIDFromBytes(
            ("source-product:" + input.sourceSystem() + ":" + sourceRef)
                .getBytes(StandardCharsets.UTF_8));
    Optional<SourceProduct> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), sourceRef);

    if (current.isPresent()) {
      SourceProduct existing = current.get();
      if (existing.sameFact(input)) {
        return new SourceProductIdentity(existing.logicalEntityId(), sourceRef);
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    repository.save(
        SourceProduct.create(
            logicalId,
            input.sourceSystem(),
            sourceRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.sourceProductId(),
            input.name(),
            input.sku(),
            input.category()));
    return new SourceProductIdentity(logicalId, sourceRef);
  }
}
