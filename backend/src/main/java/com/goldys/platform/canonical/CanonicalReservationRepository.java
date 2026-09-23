package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
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
}
