package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalGuestRepository extends BitemporalRepository<CanonicalGuest> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select g from CanonicalGuest g where g.sourceRecordRef = :ref "
          + "and g.sourceSystem = :source and g.supersededAt is null")
  Optional<CanonicalGuest> lockCurrentGuest(String ref, String source);

  @Query("select g from CanonicalGuest g where g.sourceRecordRef = :ref and g.supersededAt is null")
  List<CanonicalGuest> findCurrentByRef(String ref);
}
