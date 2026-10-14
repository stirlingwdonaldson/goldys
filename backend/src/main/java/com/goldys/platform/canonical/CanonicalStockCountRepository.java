package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalStockCountRepository extends BitemporalRepository<CanonicalStockCount> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from CanonicalStockCount c where c.sourceRecordRef = :ref "
          + "and c.sourceSystem = :source and c.supersededAt is null")
  Optional<CanonicalStockCount> lockCurrentSourceFact(String source, String ref);
}
