package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** The re-runnable query parameters, for persisting this widget in a saved dashboard. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("startDate", startDate.toString());
    map.put("endDate", endDate.toString());
    map.put("metric", metric.name());
    return map;
  }
}
