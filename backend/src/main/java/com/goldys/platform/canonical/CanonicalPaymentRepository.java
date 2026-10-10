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

interface CanonicalPaymentRepository extends BitemporalRepository<CanonicalPayment> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from CanonicalPayment p where p.sourceRecordRef = :ref "
          + "and p.sourceSystem = :source and p.supersededAt is null")
  Optional<CanonicalPayment> lockCurrentPayment(String ref, String source);

  @Query("select p from CanonicalPayment p where p.supersededAt is null")
  List<CanonicalPayment> findAllCurrent();

  @Query(
      "select p from CanonicalPayment p where p.supersededAt is null and p.tradingDate in :dates")
  List<CanonicalPayment> findCurrentByTradingDateIn(Collection<LocalDate> dates);

  @Query(
      """
      select p from CanonicalPayment p
      where p.supersededAt is null
        and p.tradingDate >= :from and p.tradingDate <= :to
        and (:paymentType is null or p.paymentTypeName = :paymentType)
        and (:saleNumber is null or p.saleNumber = :saleNumber)
      order by p.tradingDate desc, p.paymentTypeName asc, p.saleNumber asc
      """)
  Page<CanonicalPayment> findCurrent(
      LocalDate from, LocalDate to, String paymentType, String saleNumber, Pageable pageable);
}
