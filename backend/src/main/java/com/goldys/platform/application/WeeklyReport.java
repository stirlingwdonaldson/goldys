package com.goldys.platform.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The same authorised report snapshot is rendered on screen and exported to Excel. */
public record WeeklyReport(
    LocalDate from, LocalDate to, Instant generatedAt, String timezone, List<Week> weeks) {
  public record Week(LocalDate start, LocalDate end, List<LocalDate> dates, List<Row> rows) {}

  public record Row(
      String id,
      String section,
      String label,
      String unit,
      List<BigDecimal> days,
      BigDecimal total,
      String status,
      List<String> notes) {}
}
