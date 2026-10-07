package com.goldys.platform.semantic.catalog;

import java.util.List;

/** One series of points. {@code dimensionValue} is null for an ungrouped series. */
public record MetricSeries(String dimensionValue, List<MetricPoint> points) {
  public MetricSeries {
    points = points == null ? List.of() : List.copyOf(points);
  }
}
