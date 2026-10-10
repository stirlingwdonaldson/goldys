package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalSaleItemRepository extends BitemporalRepository<CanonicalSaleItem> {
  List<CanonicalSaleItem> findAllBySourceSystemAndSourceRecordRefOrderByRecordedAt(
      String sourceSystem, String sourceRecordRef);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalSaleItem s where s.sourceSystem = :source "
          + "and s.sourceRecordRef = :sourceRef and s.supersededAt is null")
  Optional<CanonicalSaleItem> lockCurrentSourceFact(String source, String sourceRef);

  @Query("select s from CanonicalSaleItem s where s.supersededAt is null")
  List<CanonicalSaleItem> findAllCurrent();

  @Query(
      "select s from CanonicalSaleItem s where s.supersededAt is null and s.tradingDate in :dates")
  List<CanonicalSaleItem> findCurrentByTradingDateIn(Collection<LocalDate> dates);

  @Query(
      "select s from CanonicalSaleItem s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalSaleItem> findCurrent(UUID logicalId, String source);

  @Query(
      "select s from CanonicalSaleItem s where s.logicalEntityId = :logicalId "
          + "and s.sourceSystem = :source and s.recordedAt <= :asOf "
          + "and (s.supersededAt is null or s.supersededAt > :asOf)")
  Optional<CanonicalSaleItem> findKnownAt(UUID logicalId, String source, Instant asOf);

  @Query(
      """
      select s from CanonicalSaleItem s
      where s.supersededAt is null
        and s.tradingDate >= :from and s.tradingDate <= :to
        and (:category is null or s.categoryName = :category)
        and (:saleNumber is null or s.saleNumber = :saleNumber)
      order by s.tradingDate desc, s.sourceRecordRef asc
      """)
  Page<CanonicalSaleItem> findCurrent(
      LocalDate from, LocalDate to, String category, String saleNumber, Pageable pageable);
}
