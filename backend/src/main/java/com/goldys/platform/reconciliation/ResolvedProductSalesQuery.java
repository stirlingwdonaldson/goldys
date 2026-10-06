package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import com.goldys.platform.semantic.TopSeller;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link ProductMetricsQuery} backed by the resolved product-sales projection. */
@Service
public class ResolvedProductSalesQuery implements ProductMetricsQuery {
  private final ResolvedProductSalesRepository repository;

  public ResolvedProductSalesQuery(ResolvedProductSalesRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<TopSeller> topSellers(LocalDate from, LocalDate to, int limit) {
    List<ResolvedProductSales> rows =
        repository.findByTradingDateBetweenOrderByTradingDateAscProductNameKeyAsc(from, to);

    Map<String, MutableTotal> byProduct = new LinkedHashMap<>();
    for (ResolvedProductSales r : rows) {
      MutableTotal total = byProduct.computeIfAbsent(r.productNameKey(), k -> new MutableTotal());
      if (r.amount() != null) {
        total.amount = nullSafeAdd(total.amount, r.amount());
        total.quantity = nullSafeAdd(total.quantity, r.quantitySold());
      }
      total.hasConflict |= r.hasConflict();
    }

    List<TopSeller> out = new ArrayList<>();
    for (Map.Entry<String, MutableTotal> e : byProduct.entrySet()) {
      out.add(
          new TopSeller(
              e.getKey(), e.getValue().quantity, e.getValue().amount, e.getValue().hasConflict));
    }
    out.sort(
        Comparator.comparing(TopSeller::amount, Comparator.nullsLast(Comparator.reverseOrder())));
    return out.stream().limit(limit).toList();
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
    return current == null ? add : current.add(add);
  }

  private static final class MutableTotal {
    BigDecimal quantity;
    BigDecimal amount;
    boolean hasConflict;
  }
}
