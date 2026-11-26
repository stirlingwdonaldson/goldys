package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record GetMerchandiseHistoryInput(
    Mode mode, LocalDate startDate, LocalDate endDate, int limit) implements ToolInput {
  public enum Mode {
    COVERAGE,
    PRODUCTS
  }

  public GetMerchandiseHistoryInput {
    if (mode == null) throw new IllegalArgumentException("mode is required");
    if (mode == Mode.COVERAGE) {
      if (startDate != null || endDate != null)
        throw new IllegalArgumentException("Coverage checks all retained dates; omit dates");
    } else if (startDate == null
        || endDate == null
        || endDate.isBefore(startDate)
        || ChronoUnit.DAYS.between(startDate, endDate) > 3660) {
      throw new IllegalArgumentException(
          "Products require an inclusive range of at most 3661 days");
    }
    if (limit == 0) limit = 20;
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1–100");
  }
}
