package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalSaleRecipeLinkRepository extends BitemporalRepository<CanonicalSaleRecipeLink> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from CanonicalSaleRecipeLink l where l.sourceRecordRef = :ref "
          + "and l.sourceSystem = :source and l.supersededAt is null")
  Optional<CanonicalSaleRecipeLink> lockCurrentSaleRecipeLink(String ref, String source);

  @Query("select l from CanonicalSaleRecipeLink l where l.supersededAt is null")
  List<CanonicalSaleRecipeLink> findAllCurrent();
}
