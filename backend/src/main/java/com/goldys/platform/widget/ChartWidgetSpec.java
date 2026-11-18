package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** A server-computed chart; options select reviewed renderers, never executable UI. */
public record ChartWidgetSpec(
    String id,
    String title,
    String description,
    ChartKind kind,
    List<Series> series,
    String yFormat,
    boolean stacked,
    boolean horizontal,
    WidgetQuery query)
    implements WidgetSpec {
  public ChartWidgetSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(kind, "kind");
    series = List.copyOf(series);
  }

  @Override
  @JsonProperty(value = "schemaVersion", access = JsonProperty.Access.READ_ONLY)
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }
}
