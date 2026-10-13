package com.goldys.platform.semantic.catalog;

import java.util.Set;

/**
 * The bounded query behind a panel: a metric, a time range, a grain, and optional dimensions and
 * comparison. There is no column/SQL/filter surface.
 */
public record MetricQuery(
    MetricId metric,
    TimeRange range,
    TimeGrain grain,
    Set<Dimension> dimensions,
    Comparison comparison) {
  public MetricQuery {
    if (metric == null) {
      throw new IllegalArgumentException("Unknown metric: null");
    }
    if (range == null) {
      throw new IllegalArgumentException("range is required");
    }
    if (grain == null) {
      throw new IllegalArgumentException("grain is required");
    }
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
  }
}
