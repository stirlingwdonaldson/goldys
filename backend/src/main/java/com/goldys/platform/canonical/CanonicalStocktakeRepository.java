package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalStocktakeRepository extends BitemporalRepository<CanonicalStocktake> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalStocktake s where s.sourceRecordRef = :ref "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalStocktake> lockCurrentSourceFact(String source, String ref);
}
