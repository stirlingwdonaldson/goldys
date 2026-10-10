package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved sale items. Implementations read the resolved projection; consumers
 * never derive these figures from canonical source rows.
 */
public interface SaleItemMetricsQuery {

  /** Resolved sale-item mix for the inclusive range, ascending by date then category. */
  List<SaleItemMix> dailyByCategory(LocalDate from, LocalDate to);
}
