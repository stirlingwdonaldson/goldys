package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Objects;

/** A time-series (or multi-series, when a dimension groups the result) metric result. */
public record TimeSeriesResult(
    MetricId metric, List<MetricSeries> series, List<String> notices, MetricProvenance provenance)
    implements MetricResult {
  public TimeSeriesResult {
    Objects.requireNonNull(metric, "metric");
    series = series == null ? List.of() : List.copyOf(series);
    notices = notices == null ? List.of() : List.copyOf(notices);
    Objects.requireNonNull(provenance, "provenance");
  }
}
