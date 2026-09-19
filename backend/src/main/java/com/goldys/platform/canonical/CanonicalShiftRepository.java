package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for canonical shifts, mirroring the sale-item current/as-of pattern on the shared
 * bitemporal base. No matching tolerances are encoded here.
 */
interface CanonicalShiftRepository extends BitemporalRepository<CanonicalShift> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalShift s where s.sourceSystem = :source "
          + "and s.sourceRecordRef = :sourceRef and s.supersededAt is null")
  Optional<CanonicalShift> lockCurrentSourceFact(String source, String sourceRef);

  @Query(
      "select s from CanonicalShift s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalShift> findCurrent(UUID logicalId, String source);

  @Query(
      "select s from CanonicalShift s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.recordedAt <= :asOf "
          + "and (s.supersededAt is null or s.supersededAt > :asOf)")
  Optional<CanonicalShift> findKnownAt(UUID logicalId, String source, Instant asOf);
}
