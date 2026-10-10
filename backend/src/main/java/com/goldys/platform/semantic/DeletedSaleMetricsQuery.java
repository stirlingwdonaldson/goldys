package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved deleted sales. Implementations read the resolved projection;
 * consumers never derive these figures from canonical source rows.
 */
public interface DeletedSaleMetricsQuery {

  /** Resolved deleted-sale totals for the inclusive range, ascending by date. */
  List<DeletedSaleDay> dailyTotals(LocalDate from, LocalDate to);
}
