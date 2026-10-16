package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SourceProductRepository extends BitemporalRepository<SourceProduct> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from SourceProduct p where p.sourceRecordRef = :ref "
          + "and p.sourceSystem = :source and p.supersededAt is null")
  Optional<SourceProduct> lockCurrentSourceFact(String source, String ref);
}
