package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalProductSalesRepository extends BitemporalRepository<CanonicalProductSales> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalProductSales s where s.productNameKey = :key "
          + "and s.tradingDate = :date and s.sourceSystem = :source and s.supersededAt is null")
  Optional<CanonicalProductSales> lockCurrent(String key, LocalDate date, String source);

  @Query(
      "select s from CanonicalProductSales s where s.tradingDate = :date and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDate(LocalDate date);

  @Query("select s from CanonicalProductSales s where s.supersededAt is null")
  List<CanonicalProductSales> findAllCurrent();
}
