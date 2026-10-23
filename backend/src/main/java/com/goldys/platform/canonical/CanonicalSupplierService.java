package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a source supplier to a stable logical identity. The identity key is the source's own
 * supplier code when present; a missing code falls back to a name-derived key so two suppliers with
 * the same name but no code are still one entity, while two suppliers with different codes never
 * merge (name is never identity). A rename supersedes the row and keeps {@code logicalEntityId}.
 */
@Service
class CanonicalSupplierService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalSupplierRepository repository;

  CanonicalSupplierService(CanonicalSupplierRepository repository) {
    this.repository = repository;
  }

  @Transactional
  SupplierIdentity record(SupplierInput input) {
    return recordAt(input, CLOCK.instant());
  }

  @Transactional
  SupplierIdentity recordAt(SupplierInput input, Instant recordedAt) {
    String sourceRef = sourceRefFor(input);
    UUID logicalId = logicalIdFor(input.sourceSystem(), sourceRef);
    Optional<CanonicalSupplier> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), sourceRef);

    if (current.isPresent()) {
      CanonicalSupplier existing = current.get();
      if (existing.sameFact(input)) {
        return new SupplierIdentity(existing.logicalEntityId(), sourceRef);
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    repository.save(
        CanonicalSupplier.create(
            logicalId,
            input.sourceSystem(),
            sourceRef,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.supplierCode(),
            input.supplierName()));
    return new SupplierIdentity(logicalId, sourceRef);
  }

  /**
   * The supplier's source-scoped key: the source's supplier code when present, otherwise a
   * name-derived key. The name fallback is deliberately prefixed so it can never collide with a
   * real code and is visible in the identity for review.
   */
  static String sourceRefFor(SupplierInput input) {
    String code = input.supplierCode();
    if (code != null && !code.isBlank()) {
      return code.trim();
    }
    String name = input.supplierName();
    String normalized =
        name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    return "name:" + normalized;
  }

  private static UUID logicalIdFor(String sourceSystem, String sourceRef) {
    return UUID.nameUUIDFromBytes(
        ("supplier:" + sourceSystem + ":" + sourceRef).getBytes(StandardCharsets.UTF_8));
  }
}
