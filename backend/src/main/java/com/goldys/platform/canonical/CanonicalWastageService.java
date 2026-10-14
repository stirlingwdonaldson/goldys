package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical wastage facts, locking the current source fact so concurrent corrections cannot
 * leave two current versions. The logical identity is a deterministic UUID of {@code
 * (wastageRecordId, lineId)}.
 */
@Service
class CanonicalWastageService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalWastageRepository repository;

  CanonicalWastageService(CanonicalWastageRepository repository) {
    this.repository = repository;
  }

  @Transactional
  CanonicalWastage record(WastageInput input) {
    return recordAt(input, CLOCK.instant());
  }

  /** Record a batch of facts in one transaction. No event: no projector listens this slice. */
  @Transactional
  void recordAll(List<WastageInput> inputs) {
    for (WastageInput input : inputs) {
      recordAt(input, CLOCK.instant());
    }
  }

  @Transactional
  CanonicalWastage recordAt(WastageInput input, Instant recordedAt) {
    String ref = sourceRef(input.wastageRecordId(), input.lineId());
    UUID logicalId = logicalIdFor(input.wastageRecordId(), input.lineId());
    Optional<CanonicalWastage> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), ref);

    if (current.isPresent()) {
      CanonicalWastage existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalWastage.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.wastageRecordId(),
            input.lineId(),
            input.wastageDate(),
            input.productNameKey(),
            input.quantity(),
            input.cost(),
            input.reason(),
            input.supplierName(),
            input.unitOfMeasurementName(),
            input.unitSize(),
            input.recipeId()));
  }

  private static String sourceRef(int wastageRecordId, int lineId) {
    return "wastage:" + wastageRecordId + ":" + lineId;
  }

  private static UUID logicalIdFor(int wastageRecordId, int lineId) {
    return UUID.nameUUIDFromBytes(
        ("wastage:" + wastageRecordId + ":" + lineId).getBytes(StandardCharsets.UTF_8));
  }
}
