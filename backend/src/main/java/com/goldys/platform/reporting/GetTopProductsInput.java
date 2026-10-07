package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** Input for {@link ToolId#GET_TOP_PRODUCTS}: top products by sales amount over a date range. */
public record GetTopProductsInput(LocalDate startDate, LocalDate endDate, int limit)
    implements ToolInput {
  public GetTopProductsInput {
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
