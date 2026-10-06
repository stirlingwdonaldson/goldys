package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** A line/area chart of one or more series over a category/time axis. */
public record TimeSeriesWidgetSpec(
    String id,
    String title,
    String description,
    List<Series> series,
    String yFormat,
    WidgetQuery query)
    implements WidgetSpec {

  public TimeSeriesWidgetSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    series = series == null ? List.of() : List.copyOf(series);
  }

  @Override
  @JsonProperty("schemaVersion")
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }
}
