package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Reconciles per-product daily sales by normalized name + date, with zero tolerance. */
@Service
public class ProductSalesReconciliationService {
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesReconciliationService(CanonicalProductSalesQuery productSales) {
    this.productSales = productSales;
  }

  public List<ProductSalesConflict> conflicts() {
    Map<String, List<ProductSourceTotal>> byKey = new LinkedHashMap<>();
    for (ProductSalesView view : productSales.currentProductSales()) {
      byKey
          .computeIfAbsent(
              view.tradingDate() + "\u0000" + view.productNameKey(), k -> new ArrayList<>())
          .add(new ProductSourceTotal(view.sourceSystem(), view.quantitySold(), view.amount()));
    }
    List<ProductSalesConflict> out = new ArrayList<>();
    for (Map.Entry<String, List<ProductSourceTotal>> e : byKey.entrySet()) {
      String status = classify(e.getValue());
      if (!"agreed".equals(status)) {
        String[] parts = e.getKey().split("\u0000");
        out.add(
            new ProductSalesConflict(LocalDate.parse(parts[0]), parts[1], e.getValue(), status));
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
}
