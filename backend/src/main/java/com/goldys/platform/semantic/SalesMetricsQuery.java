package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business reads over resolved daily sales. Implementations read the resolved projection; consumers
 * (dashboards, reporting tools, exports) never derive these figures from canonical source rows.
 */
public interface SalesMetricsQuery {

  /** Resolved daily-sales metrics for the inclusive range, ascending by trading date. */
  List<DailySalesMetric> dailySales(LocalDate from, LocalDate to);

  /** The latest trading date's resolved metric, or empty when there is no data yet. */
  Optional<DailySalesMetric> latestTradingDay();

  /** Number of trading dates whose daily sales are still unresolved. */
  long openConflicts();
}
