package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SourceProductSaleFactRepository extends BitemporalRepository<SourceProductSaleFact> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select f from SourceProductSaleFact f where f.sourceRecordRef = :ref "
          + "and f.sourceSystem = :source and f.supersededAt is null")
  Optional<SourceProductSaleFact> lockCurrentSourceFact(String source, String ref);
}
