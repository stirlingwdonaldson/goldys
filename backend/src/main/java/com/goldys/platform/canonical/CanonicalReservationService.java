package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical reservation facts, locking the current source fact so concurrent corrections
 * cannot leave two current versions. The logical identity is a deterministic UUID of the
 * reservation id, so the same reservation from two sources shares an id without a matching step.
 */
@Service
class CanonicalReservationService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalReservationRepository repository;
  private final ApplicationEventPublisher publisher;
  private final ZoneId zone;

  CanonicalReservationService(
      CanonicalReservationRepository repository,
      ApplicationEventPublisher publisher,
      @Value("${opentable.timezone:Australia/Sydney}") String zone) {
    this.repository = repository;
    this.publisher = publisher;
    this.zone = ZoneId.of(zone);
  }

  @Transactional
  CanonicalReservation record(ReservationInput input) {
    CanonicalReservation saved = recordAt(input, CLOCK.instant());
    publisher.publishEvent(new ReservationRecorded(input.reservationAt().atZone(zone).toLocalDate()));
    return saved;
  }

  @Transactional
  CanonicalReservation recordAt(ReservationInput input, Instant recordedAt) {
    UUID logicalId = logicalIdFor(input.reservationId());
    String ref = input.reservationId();
    Optional<CanonicalReservation> current =
        repository.lockCurrentReservation(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalReservation existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(recordedAt);
      repository.saveAndFlush(existing);
    }

    return repository.save(
        CanonicalReservation.create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            recordedAt,
            recordedAt,
            input.reservationAt(),
            input.partySize(),
            input.status(),
            input.tableName(),
            input.sourceChannel(),
            input.partyName()));
  }

  private static UUID logicalIdFor(String reservationId) {
    return UUID.nameUUIDFromBytes(
        ("reservation:" + reservationId).getBytes(StandardCharsets.UTF_8));
  }
}
