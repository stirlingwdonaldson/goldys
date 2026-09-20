package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalDailySalesRepository extends BitemporalRepository<CanonicalDailySales> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalDailySales s where s.tradingDate = :date "
          + "and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalDailySales> lockCurrentDailySale(LocalDate date, String source);

  @Query(
      "select s from CanonicalDailySales s where s.logicalEntityId = :logicalId "
          + "and s.supersededAt is null")
  List<CanonicalDailySales> findCurrentByLogicalId(UUID logicalId);

  @Query(
      "select s from CanonicalDailySales s where s.tradingDate = :date "
          + "and s.supersededAt is null")
  List<CanonicalDailySales> findCurrentByDate(LocalDate date);

  @Query("select s from CanonicalDailySales s where s.supersededAt is null")
  List<CanonicalDailySales> findAllCurrent();
}
