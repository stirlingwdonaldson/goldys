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
 * Records canonical labour facts, locking the current source fact so concurrent corrections cannot
 * leave two current versions. The logical identity is a deterministic UUID of the source record
 * ref.
 */
@Service
class CanonicalLabourEntryService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalLabourEntryRepository repository;
  private final ApplicationEventPublisher publisher;

  CanonicalLabourEntryService(
      CanonicalLabourEntryRepository repository, ApplicationEventPublisher publisher) {
    this.repository = repository;
    this.publisher = publisher;
  }

  @Transactional
  CanonicalLabourEntry record(LabourInput input) {
    CanonicalLabourEntry saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new LabourRecorded(input.labourDate()));
    return saved;
  }

  @Transactional
  CanonicalLabourEntry recordAt(LabourInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.sourceRecordRef());
    Optional<CanonicalLabourEntry> current =
        repository.lockCurrentSourceFact(input.sourceSystem(), input.sourceRecordRef());

    if (current.isPresent()) {
      CanonicalLabourEntry existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalLabourEntry.create(
            logicalId,
            input.sourceSystem(),
            input.sourceRecordRef(),
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.staffRef(),
            input.department(),
            input.labourDate(),
            input.scheduledHours(),
            input.actualHours(),
            input.scheduledCost(),
            input.actualCost(),
            input.shiftStart(),
            input.shiftEnd()));
  }

  private static UUID logicalIdFor(String sourceRecordRef) {
    return UUID.nameUUIDFromBytes(("labour:" + sourceRecordRef).getBytes(StandardCharsets.UTF_8));
  }
}
