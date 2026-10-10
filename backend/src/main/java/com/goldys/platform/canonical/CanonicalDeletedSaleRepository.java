package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  @Query(
      "select d from CanonicalDeletedSale d where d.supersededAt is null and d.tradingDate in :dates")
  List<CanonicalDeletedSale> findCurrentByTradingDateIn(Collection<LocalDate> dates);

  @Query(
      """
      select d from CanonicalDeletedSale d
      where d.supersededAt is null
        and d.tradingDate >= :from and d.tradingDate <= :to
        and (:saleNumber is null or d.saleNumber = :saleNumber)
      order by d.tradingDate desc, d.saleNumber asc
      """)
  Page<CanonicalDeletedSale> findCurrent(
      LocalDate from, LocalDate to, String saleNumber, Pageable pageable);
}
