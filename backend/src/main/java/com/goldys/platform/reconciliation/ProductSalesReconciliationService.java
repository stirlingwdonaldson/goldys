package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reconciles per-product daily sales by normalized name + date, with zero tolerance. */
@Service
public class ProductSalesReconciliationService {
  private final CanonicalProductSalesQuery productSales;
  private final ProductSalesOverrideRepository overrides;
  private final ResolutionRuleService rules;

  public ProductSalesReconciliationService(
      CanonicalProductSalesQuery productSales,
      ProductSalesOverrideRepository overrides,
      ResolutionRuleService rules) {
    this.productSales = productSales;
    this.overrides = overrides;
    this.rules = rules;
  }

  public List<ProductSalesConflict> conflicts() {
    Map<String, List<ProductSourceTotal>> byKey = new LinkedHashMap<>();
    for (ProductSalesView view : productSales.currentProductSales()) {
      byKey
          .computeIfAbsent(
              view.tradingDate() + "\u0000" + view.productNameKey(), k -> new ArrayList<>())
          .add(
              new ProductSourceTotal(
                  view.sourceSystem(), view.quantitySold(), view.amount(), view.recordedAt()));
    }
    List<ProductSalesConflict> out = new ArrayList<>();
    for (Map.Entry<String, List<ProductSourceTotal>> e : byKey.entrySet()) {
      String[] parts = e.getKey().split("\u0000");
      LocalDate date = LocalDate.parse(parts[0]);
      String key = parts[1];
      // An override resolves this product/day, so it drops out of the open-exceptions list.
      if (overrides.findCurrent(key, date).isPresent()) {
        continue;
      }
      String status = classify(e.getValue());
      if (!"agreed".equals(status)) {
        // A product-specific rule wins over the "*" catch-all.
        Optional<ResolutionRule> rule = rules.findCurrent("product_sales", key);
        if (rule.isEmpty()) {
          rule = rules.findCurrent("product_sales", "*");
        }
        boolean resolvedByRule =
            rule.flatMap(r -> RuleEvaluator.resolve(r, toProductMetrics(e.getValue()))).isPresent();
        if (!resolvedByRule) {
          out.add(new ProductSalesConflict(date, key, e.getValue(), status));
        }
      }
    }
    out.sort(
        Comparator.comparing(ProductSalesConflict::tradingDate)
            .thenComparing(ProductSalesConflict::productNameKey));
    return out;
  }

  static String classify(List<ProductSourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    ProductSourceTotal first = sources.get(0);
    boolean agree =
        sources.stream()
            .allMatch(
                s ->
                    first.quantitySold().compareTo(s.quantitySold()) == 0
                        && first.amount().compareTo(s.amount()) == 0);
    return agree ? "agreed" : "conflict";
  }

  private static List<SourceMetric> toProductMetrics(List<ProductSourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.quantitySold(), s.recordedAt()))
        .toList();
  }
}
