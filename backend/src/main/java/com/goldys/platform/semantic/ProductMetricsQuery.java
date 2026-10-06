package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business reads over resolved product sales. Implementations read the resolved projection;
 * consumers never derive these figures from canonical source rows.
 */
public interface ProductMetricsQuery {

  /** Top products by summed resolved amount over the inclusive range, descending. */
  List<TopSeller> topSellers(LocalDate from, LocalDate to, int limit);

  /** Resolved product-sales metrics for the inclusive range, ordered by date then product. */
  List<ProductSalesMetric> productSales(LocalDate from, LocalDate to);

  /** The resolved metric for a specific date and product, or empty when not projected. */
  Optional<ProductSalesMetric> productSales(LocalDate date, String productName);

  /** Number of product/days that are still unresolved. */
  long openConflicts();
}
