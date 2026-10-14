package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical guest facts, locking the current source fact so concurrent ingest cannot leave
 * two current versions. The logical identity is a deterministic UUID of the source Guest ID, the
 * same {@code nameUUIDFromBytes("guest:" + id)} value {@link CanonicalReview} denormalizes into its
 * {@code guest_id} column, so a review links to its guest without a matching step.
 *
 * <p>Reviews carry only a minimal guest fact (a name alongside the Guest ID), so there is nothing
 * to supersede: the first version is the only version, and a repeat {@code ensure} is a no-op.
 */
@Service
class CanonicalGuestService {
  private final CanonicalGuestRepository repository;
  private final Clock clock;

  @Autowired
  CanonicalGuestService(CanonicalGuestRepository repository) {
    this(repository, Clock.systemUTC());
  }

  /** Test-friendly constructor with an explicit clock, so tests control recordedAt. */
  CanonicalGuestService(CanonicalGuestRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  void ensure(String guestId, String guestName, UUID rawRecordId) {
    ensure(guestId, guestName, rawRecordId, clock.instant());
  }

  @Transactional
  void ensure(String guestId, String guestName, UUID rawRecordId, Instant now) {
    UUID logicalId = UUID.nameUUIDFromBytes(("guest:" + guestId).getBytes(StandardCharsets.UTF_8));
    Optional<CanonicalGuest> current = repository.lockCurrentGuest(guestId, "OPENTABLE");
    if (current.isPresent()) {
      // A review-sourced guest is minimal; nothing to supersede.
      return;
    }
    repository.save(
        CanonicalGuest.create(
            logicalId,
            "OPENTABLE",
            guestId,
            rawRecordId,
            now,
            now,
            guestName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));
  }
}
