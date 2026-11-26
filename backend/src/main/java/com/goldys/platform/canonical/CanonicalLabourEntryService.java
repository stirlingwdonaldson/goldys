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

    var entry =
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
            input.shiftEnd());
    entry.measure(input.measure());
    return repository.save(entry);
  }

  /** Publish a fully captured source window atomically, including deletions and moved shifts. */
  @Transactional
  void synchronize(
      String source,
      java.time.LocalDate from,
      java.time.LocalDate to,
      java.util.List<LabourInput> inputs) {
    var refs = new java.util.HashSet<String>();
    for (var input : inputs) {
      if (!source.equals(input.sourceSystem())
          || input.labourDate().isBefore(from)
          || input.labourDate().isAfter(to)
          || !refs.add(input.sourceRecordRef()))
        throw new IllegalArgumentException("Invalid labour snapshot");
    }
    Instant now = CLOCK.instant();
    var dates = new java.util.HashSet<java.time.LocalDate>();
    for (var existing : repository.findAllCurrent()) {
      if (!source.equals(existing.sourceSystem())) continue;
      boolean inWindow =
          !existing.labourDate().isBefore(from) && !existing.labourDate().isAfter(to);
      if (inWindow || refs.contains(existing.sourceRecordRef())) dates.add(existing.labourDate());
      if (inWindow && !refs.contains(existing.sourceRecordRef())) {
        existing.supersede(now);
        repository.save(existing);
      }
    }
    for (var input : inputs) {
      recordAt(input, now);
      dates.add(input.labourDate());
    }
    repository.flush();
    for (var date : dates) publisher.publishEvent(new LabourRecorded(date));
  }

  private static UUID logicalIdFor(String sourceRecordRef) {
    return UUID.nameUUIDFromBytes(("labour:" + sourceRecordRef).getBytes(StandardCharsets.UTF_8));
  }
}
