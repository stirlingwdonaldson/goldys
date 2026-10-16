package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalProductSalesRepository extends BitemporalRepository<CanonicalProductSales> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalProductSales s where s.productNameKey = :key "
          + "and s.tradingDate = :date and s.sourceSystem = :source and s.sourceProductRef is null and s.supersededAt is null")
  Optional<CanonicalProductSales> lockCurrent(String key, LocalDate date, String source);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from CanonicalProductSales s where s.sourceSystem=:source and s.sourceRecordRef=:ref and s.supersededAt is null")
  Optional<CanonicalProductSales> lockQualified(String source, String ref);

  @org.springframework.data.jpa.repository.Modifying
  @Query(
      value =
          "insert into product_sales_contributor(canonical_version_id, raw_record_id) values (:versionId,:rawId) on conflict do nothing",
      nativeQuery = true)
  void addContributor(UUID versionId, UUID rawId);

  @Query(
      value =
          "select exists(select 1 from product_sales_contributor c join canonical_product_sales p on p.id=c.canonical_version_id where p.source_system=:source and p.source_record_ref=:ref and c.raw_record_id=:rawId)",
      nativeQuery = true)
  boolean observed(String source, String ref, UUID rawId);

  @Query(
      value =
          "select exists(select 1 from product_sales_contributor c join canonical_product_sales p on p.id=c.canonical_version_id join raw_record later on later.id=c.raw_record_id join raw_record incoming on incoming.id=:rawId where p.source_system=:source and p.source_record_ref=:ref and (later.fetched_at,later.id)>(incoming.fetched_at,incoming.id))",
      nativeQuery = true)
  boolean hasLaterObservation(String source, String ref, UUID rawId);

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

  /**
   * The distinct trading dates of canonical product-sales rows (any version, superseded or not)
   * that cite the given raw record. Used by the source-product-sale-fact replay to recover the
   * trading date of a retained sale-item page whose request window was never persisted on the raw
   * record — the date is durable in the canonical provenance, not inferred.
   */
  @Query(
      value =
          "select distinct cast(s.trading_date as text) from canonical_product_sales s left join product_sales_contributor c on c.canonical_version_id=s.id where s.raw_record_id=:rawRecordId or c.raw_record_id=:rawRecordId",
      nativeQuery = true)
  List<String> findTradingDatesByRawRecordId(UUID rawRecordId);
}
