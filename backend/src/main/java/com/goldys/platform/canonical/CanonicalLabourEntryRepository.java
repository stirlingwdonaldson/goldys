package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalLabourEntryRepository extends BitemporalRepository<CanonicalLabourEntry> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from CanonicalLabourEntry e where e.sourceRecordRef = :ref "
          + "and e.sourceSystem = :source and e.supersededAt is null")
  Optional<CanonicalLabourEntry> lockCurrentSourceFact(String source, String ref);

  @Query("select e from CanonicalLabourEntry e where e.supersededAt is null")
  List<CanonicalLabourEntry> findAllCurrent();
}
