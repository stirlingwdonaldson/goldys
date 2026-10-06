package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.ProductSalesView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read-only facade over the product_sales exception projection, joined back to canonical for the
 * per-source values shown in the exceptions UI. */
@Service
public class ProductSalesExceptionQuery {
  private static final String ENTITY_TYPE = "product_sales";
  private static final String SEP = "\u0000";

  private final ReconciliationExceptionRowRepository exceptions;
  private final CanonicalProductSalesQuery productSales;

  public ProductSalesExceptionQuery(
      ReconciliationExceptionRowRepository exceptions, CanonicalProductSalesQuery productSales) {
    this.exceptions = exceptions;
    this.productSales = productSales;
  }

  public List<ProductException> listAll() {
    List<ReconciliationExceptionRow> rows =
        exceptions.findByEntityTypeOrderByTradingDateDesc(ENTITY_TYPE);
    if (rows.isEmpty()) {
      return List.of();
    }
    Set<LocalDate> dates = new LinkedHashSet<>();
    for (ReconciliationExceptionRow row : rows) {
      dates.add(row.tradingDate());
    }
    Map<String, List<ProductSourceTotal>> byPair = new HashMap<>();
    for (ProductSalesView v : productSales.currentProductSalesForDates(dates)) {
      byPair
          .computeIfAbsent(v.tradingDate() + SEP + v.productNameKey(), k -> new ArrayList<>())
          .add(new ProductSourceTotal(v.sourceSystem(), v.quantitySold(), v.amount(), v.recordedAt()));
    }
    List<ProductException> out = new ArrayList<>();
    for (ReconciliationExceptionRow row : rows) {
      out.add(
          new ProductException(
              row.tradingDate(),
              row.entityKey(),
              row.status(),
              byPair.getOrDefault(row.tradingDate() + SEP + row.entityKey(), List.of())));
    }
    return out;
  }

  public long countOpen() {
    return exceptions.countByEntityType(ENTITY_TYPE);
  }

  public record ProductException(
      LocalDate tradingDate,
      String productNameKey,
      String status,
      List<ProductSourceTotal> sources) {}
}
