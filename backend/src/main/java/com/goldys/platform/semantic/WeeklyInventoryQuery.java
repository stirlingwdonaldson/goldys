package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Aggregates over source-qualified resolved observations, without inventing stock balances. */
public interface WeeklyInventoryQuery {
  record Waste(
      LocalDate date,
      String reason,
      long lines,
      long valuedLines,
      BigDecimal value,
      String nature) {
    public Waste(LocalDate date, String reason, long lines, long valuedLines, BigDecimal value) {
      this(date, reason, lines, valuedLines, value, "UNKNOWN");
    }
  }

  record StockCount(
      LocalDate date,
      int stocktakeId,
      long lines,
      long valuedLines,
      BigDecimal value,
      Boolean partial,
      Boolean locked) {}

  List<Waste> wastage(LocalDate from, LocalDate to);

  List<StockCount> stockCounts(LocalDate from, LocalDate to);
}
