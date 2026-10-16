package com.goldys.platform.canonical;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical per-product sales.
 *
 * <p>Intended for reconciliation internals, provenance inspection, and projection building only.
 * Business-facing reporting must read {@link
 * com.goldys.platform.reconciliation.ResolvedProductSalesQuery} instead — never aggregate canonical
 * rows for a metric, since multiple sources can describe the same product/day.
 */
@Service
public class CanonicalProductSalesQuery {
  private final CanonicalProductSalesRepository repository;

  public CanonicalProductSalesQuery(CanonicalProductSalesRepository repository) {
    this.repository = repository;
  }

  public List<ProductSalesView> currentProductSales() {
    return compatible(repository.findAllCurrent());
  }

  public List<ProductSalesView> currentProductSalesForDate(LocalDate date) {
    return compatible(repository.findCurrentByDate(date));
  }

  public List<ProductSalesView> currentProductSalesForDateAndProduct(LocalDate date, String key) {
    return compatible(repository.findCurrentByDate(date)).stream()
        .filter(v -> key.equals(v.productNameKey()))
        .toList();
  }

  public List<ProductSalesView> currentProductSalesForDates(Collection<LocalDate> dates) {
    return compatible(repository.findCurrentByDates(dates));
  }

  /** The distinct, sorted product name keys that currently have canonical product sales. */
  public List<String> distinctProductNameKeys() {
    return repository.findDistinctCurrentProductNameKeys();
  }

  /**
   * The trading dates of canonical product-sales rows that cite this raw record (any version). One
   * sale-item page belongs to a single trading day, so this returns at most one distinct date; an
   * empty or multi-date result means the record's day cannot be durably recovered.
   */
  public List<LocalDate> tradingDatesForRawRecord(UUID rawRecordId) {
    return repository.findTradingDatesByRawRecordId(rawRecordId).stream()
        .map(LocalDate::parse)
        .toList();
  }

  private List<ProductSalesView> compatible(List<CanonicalProductSales> rows) {
    java.util.Set<String> qualifiedDays = new java.util.HashSet<>();
    for (CanonicalProductSales row : rows)
      if (row.sourceProductRef() != null) qualifiedDays.add(scope(row));
    return rows.stream()
        .filter(row -> row.sourceProductRef() != null || !qualifiedDays.contains(scope(row)))
        .map(this::toView)
        .toList();
  }

  private static String scope(CanonicalProductSales row) {
    return row.sourceSystem() + "\u001f" + row.tradingDate();
  }

  private ProductSalesView toView(CanonicalProductSales s) {
    return new ProductSalesView(
        s.sourceSystem(),
        s.tradingDate(),
        s.productNameKey(),
        s.quantitySold(),
        s.amount(),
        s.recordedAt());
  }
}
