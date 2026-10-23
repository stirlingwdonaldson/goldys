package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductRanking;
import com.goldys.platform.semantic.ProductSalesMetric;
import com.goldys.platform.semantic.TopSeller;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCoverage;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** {@link ProductMetricsQuery} backed by the resolved product-sales projection. */
@Service
public class ResolvedProductSalesQuery implements ProductMetricsQuery {
  private final ResolvedProductSalesRepository repository;

  public ResolvedProductSalesQuery(ResolvedProductSalesRepository repository) {
    this.repository = repository;
  }

  @Override
  public ProductRanking qualifiedTopSellers(LocalDate from, LocalDate to, int limit) {
    new TimeRange(from, to, Calendar.CALENDAR);
    if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    List<ResolvedProductSales> rows =
        repository.findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(from, to);

    Map<String, MutableTotal> byProduct = new LinkedHashMap<>();
    Set<LocalDate> received = new HashSet<>();
    Set<LocalDate> unresolved = new HashSet<>();
    for (ResolvedProductSales r : rows) {
      received.add(r.tradingDate());
      if (r.amount() == null) unresolved.add(r.tradingDate());
      MutableTotal total = byProduct.computeIfAbsent(r.productNameKey(), k -> new MutableTotal());
      if (r.amount() != null) {
        total.amount = nullSafeAdd(total.amount, r.amount());
        total.quantity = nullSafeAdd(total.quantity, r.quantitySold());
      }
      total.hasConflict |= r.hasConflict();
    }

    List<TopSeller> out = new ArrayList<>();
    for (Map.Entry<String, MutableTotal> e : byProduct.entrySet()) {
      if (e.getValue().amount == null) continue;
      out.add(
          new TopSeller(
              e.getKey(), e.getValue().quantity, e.getValue().amount, e.getValue().hasConflict));
    }
    out.sort(
        Comparator.comparing(TopSeller::amount, Comparator.reverseOrder())
            .thenComparing(TopSeller::productName));
    // Calendar presence is evidence only: neither missing products nor within-day gaps are known.
    List<LocalDate> dates = from.datesUntil(to.plusDays(1)).toList();
    MetricCoverage coverage =
        new MetricCoverage(
            from,
            to,
            dates.stream().filter(d -> received.contains(d) && !unresolved.contains(d)).toList(),
            dates.stream().filter(unresolved::contains).toList(),
            dates.stream().filter(d -> !received.contains(d)).toList());
    List<String> notices = new ArrayList<>(coverage.notices());
    notices.add("Ranked by known sales; full-period ranking unverified.");
    notices.add(
        "An absent product row does not imply zero sales; product identity remains name-based and revenue tax basis is source-qualified.");
    long excluded = byProduct.size() - out.size();
    if (excluded > 0)
      notices.add(excluded + " product(s) excluded from ranking: revenue unavailable.");
    return new ProductRanking(out.stream().limit(limit).toList(), coverage, notices);
  }

  @Override
  public List<ProductSalesMetric> productSales(LocalDate from, LocalDate to) {
    return repository
        .findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(from, to)
        .stream()
        .map(this::toMetric)
        .toList();
  }

  @Override
  public Optional<ProductSalesMetric> productSales(LocalDate date, String productName) {
    return repository.findByTradingDateAndProductNameKey(date, productName).map(this::toMetric);
  }

  @Override
  public long openConflicts() {
    return repository.countByHasConflictTrue();
  }

  private ProductSalesMetric toMetric(ResolvedProductSales r) {
    return new ProductSalesMetric(
        r.tradingDate(),
        r.productNameKey(),
        r.quantitySold(),
        r.amount(),
        r.authoritativeSource(),
        r.hasConflict());
  }

  private static BigDecimal nullSafeAdd(BigDecimal current, BigDecimal add) {
    if (add == null) return current;
    return current == null ? add : current.add(add);
  }

  private static final class MutableTotal {
    BigDecimal quantity;
    BigDecimal amount;
    boolean hasConflict;
  }
}
