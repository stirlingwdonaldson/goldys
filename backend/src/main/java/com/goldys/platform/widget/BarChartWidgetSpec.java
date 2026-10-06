package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** A bar chart of one or more series (stacked when {@code stacked}). */
public record BarChartWidgetSpec(
    String id,
    String title,
    String description,
    List<Series> series,
    String yFormat,
    boolean stacked,
    WidgetQuery query)
    implements WidgetSpec {

  public BarChartWidgetSpec {
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
