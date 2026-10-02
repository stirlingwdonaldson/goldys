package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** Input for {@link ToolId#GET_SALES_BY_PERIOD}. */
public record GetSalesByPeriodInput(LocalDate startDate, LocalDate endDate, Metric metric)
    implements ToolInput {
  public GetSalesByPeriodInput {
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    Objects.requireNonNull(metric, "metric");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }
}
