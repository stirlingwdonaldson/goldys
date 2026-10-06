package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved labour. Implementations read the resolved projection; consumers
 * never derive these figures from canonical source rows. Single-domain only — cross-domain metrics
 * (labour % of sales, hours/cost per cover) live in the application layer.
 */
public interface LabourMetricsQuery {

  /** Resolved daily labour for the inclusive range, ascending by date then department. */
  List<LabourMetric> dailyLabour(LocalDate from, LocalDate to);

  /** Total scheduled hours over the inclusive range. */
  BigDecimal scheduledHours(LocalDate from, LocalDate to);

  /** Total actual hours over the inclusive range. */
  BigDecimal actualHours(LocalDate from, LocalDate to);

  /** Total labour cost over the inclusive range, or null when any day's cost is unknown. */
  BigDecimal labourCost(LocalDate from, LocalDate to);

  /** Scheduled − actual hours over the inclusive range. */
  BigDecimal scheduledVsActualVariance(LocalDate from, LocalDate to);
}
