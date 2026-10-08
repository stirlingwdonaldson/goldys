package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Input for {@link ToolId#GET_LABOUR_VARIANCE}. */
public record GetLabourVarianceInput(LocalDate startDate, LocalDate endDate) implements ToolInput {
  public GetLabourVarianceInput {
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }

  /** The re-runnable query parameters, for persisting this widget in a saved dashboard. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("startDate", startDate.toString());
    map.put("endDate", endDate.toString());
    return map;
  }
}
