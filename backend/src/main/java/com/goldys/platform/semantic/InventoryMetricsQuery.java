package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved inventory. Implementations read the resolved projection; consumers
 * never derive these figures from canonical source rows. Single-domain only — food-cost % (COGS ÷
 * sales) lives in the application layer.
 */
public interface InventoryMetricsQuery {

  /** Resolved daily inventory for the inclusive range, ascending by date. */
  List<InventoryMetric> dailyInventory(LocalDate from, LocalDate to);

  /** Total purchases (COGS) over the inclusive range. */
  BigDecimal purchases(LocalDate from, LocalDate to);

  /** Total wastage over the inclusive range, or null when there is no wastage data. */
  BigDecimal wastage(LocalDate from, LocalDate to);
}
