package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductMappingRepository extends BitemporalRepository<ProductMapping> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from ProductMapping m where m.sourceRecordRef = :ref "
          + "and m.sourceSystem = :source and m.supersededAt is null")
  Optional<ProductMapping> lockCurrentSourceFact(String source, String ref);
}
