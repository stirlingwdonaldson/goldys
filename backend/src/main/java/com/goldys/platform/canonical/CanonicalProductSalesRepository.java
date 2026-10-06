package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

  @Query(
      "select distinct p.productNameKey from CanonicalProductSales p where p.supersededAt is null "
          + "order by p.productNameKey")
  List<String> findDistinctCurrentProductNameKeys();

  @Query(
      "select s from CanonicalProductSales s where s.tradingDate = :date "
          + "and s.productNameKey = :key and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDateAndProduct(LocalDate date, String key);

  @Query(
      "select s from CanonicalProductSales s where s.tradingDate in :dates and s.supersededAt is null")
  List<CanonicalProductSales> findCurrentByDates(Collection<LocalDate> dates);

  @Query(
      "select new com.goldys.platform.canonical.ProductSalesTotal("
          + "s.productNameKey, sum(s.quantitySold), sum(s.amount)) "
          + "from CanonicalProductSales s where s.supersededAt is null and s.tradingDate >= :since "
          + "group by s.productNameKey order by sum(s.amount) desc")
  List<ProductSalesTotal> topProductsByAmountSince(LocalDate since, Pageable pageable);
}
