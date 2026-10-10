package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalDeletedSaleRepository extends BitemporalRepository<CanonicalDeletedSale> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from CanonicalDeletedSale d where d.sourceRecordRef = :ref "
          + "and d.sourceSystem = :source and d.supersededAt is null")
  Optional<CanonicalDeletedSale> lockCurrentDeletedSale(String ref, String source);

  @Query("select d from CanonicalDeletedSale d where d.supersededAt is null")
  List<CanonicalDeletedSale> findAllCurrent();
}
