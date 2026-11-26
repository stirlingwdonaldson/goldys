package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Product names are bounded literal values, never SQL, regex or filter expressions. */
public record GetMerchandiseHistoryInput(
    Mode mode, LocalDate startDate, LocalDate endDate, int limit, String productName, int page)
    implements ToolInput {
  public enum Mode {
    COVERAGE,
    PRODUCTS,
    SEARCH
  }

  public GetMerchandiseHistoryInput(Mode mode, LocalDate startDate, LocalDate endDate, int limit) {
    this(mode, startDate, endDate, limit, null, 0);
  }

  public GetMerchandiseHistoryInput {
    if (mode == null) throw new IllegalArgumentException("mode is required");
    if (mode == Mode.COVERAGE) {
      if (startDate != null || endDate != null || page != 0)
        throw new IllegalArgumentException(
            "Coverage checks all retained dates; omit dates and page");
    } else if (startDate == null
        || endDate == null
        || endDate.isBefore(startDate)
        || ChronoUnit.DAYS.between(startDate, endDate) > 3660) {
      throw new IllegalArgumentException(
          "Products require an inclusive range of at most 3661 days");
    }
    if (mode == Mode.SEARCH) {
      if (productName == null
          || productName.isBlank()
          || productName.length() > 100
          || productName.chars().anyMatch(Character::isISOControl))
        throw new IllegalArgumentException(
            "SEARCH requires a literal productName of 1–100 characters");
      productName = productName.strip();
    } else if (productName != null) {
      throw new IllegalArgumentException("Use SEARCH to supply productName");
    }
    if (limit == 0) limit = 20;
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1–100");
    if (page < 0 || page > 10000) throw new IllegalArgumentException("page must be 0–10000");
  }
}
