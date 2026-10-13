package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalReservationRepository extends BitemporalRepository<CanonicalReservation> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalReservation r where r.sourceRecordRef = :ref "
          + "and r.sourceSystem = :source and r.supersededAt is null")
  Optional<CanonicalReservation> lockCurrentReservation(String ref, String source);

  @Query(
      "select r from CanonicalReservation r where r.sourceRecordRef = :ref and r.supersededAt is null")
  List<CanonicalReservation> findCurrentByRef(String ref);

  @Query("select r from CanonicalReservation r where r.supersededAt is null")
  List<CanonicalReservation> findAllCurrent();

  /** Current reservations whose {@code reservation_at} falls in {@code [from, to)}. */
  @Query(
      "select r from CanonicalReservation r where r.supersededAt is null "
          + "and r.reservationAt >= :from and r.reservationAt < :to")
  List<CanonicalReservation> findCurrentByReservationAtBetween(Instant from, Instant to);
}
