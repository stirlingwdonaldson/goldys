package com.goldys.platform.widget;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A versioned, discriminated widget specification. This is the only contract the frontend renders
 * from: both built-in dashboards and AI-generated answers resolve to one of these variants. It is
 * data, never code — the AI cannot choose component names, CSS, handlers, or markup.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StatWidgetSpec.class, name = "stat"),
  @JsonSubTypes.Type(value = TimeSeriesWidgetSpec.class, name = "time-series"),
  @JsonSubTypes.Type(value = BarChartWidgetSpec.class, name = "bar-chart"),
  @JsonSubTypes.Type(value = TableWidgetSpec.class, name = "table"),
  @JsonSubTypes.Type(value = RankedListWidgetSpec.class, name = "ranked-list"),
})
public sealed interface WidgetSpec
    permits StatWidgetSpec,
        TimeSeriesWidgetSpec,
        BarChartWidgetSpec,
        TableWidgetSpec,
        RankedListWidgetSpec {

  int SCHEMA_VERSION = 2;

  int schemaVersion();

  String id();

  String title();

  String description();

  /** The semantic query that produced this widget, when it should be re-runnable (savable). */
  WidgetQuery query();
}
