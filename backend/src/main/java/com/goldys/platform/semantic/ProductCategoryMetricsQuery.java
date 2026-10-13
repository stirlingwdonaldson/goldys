package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved product sales grouped by category. Implementations read the resolved
 * projection; consumers never derive these figures from canonical source rows.
 */
public interface ProductCategoryMetricsQuery {

  /** Resolved category sales over the inclusive range. */
  List<CategorySales> salesByCategory(LocalDate from, LocalDate to);
}
