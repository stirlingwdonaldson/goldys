package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalSupplierRepository extends BitemporalRepository<CanonicalSupplier> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalSupplier s where s.sourceRecordRef = :ref "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalSupplier> lockCurrentSourceFact(String source, String ref);
}
