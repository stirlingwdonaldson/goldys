package com.goldys.platform.reporting;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Input for {@link ToolId#GET_RESERVATION_SUMMARY}. */
public record GetReservationSummaryInput(LocalDate date) implements ToolInput {
  public GetReservationSummaryInput {
    Objects.requireNonNull(date, "date");
  }

  /** The re-runnable query parameters, for persisting this widget in a saved dashboard. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("date", date.toString());
    return map;
  }
}
