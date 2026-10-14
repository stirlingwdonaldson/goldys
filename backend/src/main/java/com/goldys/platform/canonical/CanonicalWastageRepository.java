package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalWastageRepository extends BitemporalRepository<CanonicalWastage> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select w from CanonicalWastage w where w.sourceRecordRef = :ref "
          + "and w.sourceSystem = :source and w.supersededAt is null")
  Optional<CanonicalWastage> lockCurrentSourceFact(String source, String ref);
}
