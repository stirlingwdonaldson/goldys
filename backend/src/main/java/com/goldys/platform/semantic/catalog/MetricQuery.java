package com.goldys.platform.semantic.catalog;

import java.util.Objects;
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
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(range, "range");
    Objects.requireNonNull(grain, "grain");
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
  }
}
