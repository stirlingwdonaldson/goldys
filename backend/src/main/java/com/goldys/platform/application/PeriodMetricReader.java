package com.goldys.platform.application;

import com.goldys.platform.semantic.catalog.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads one catalogue period aggregate while retaining its missing-input qualifications. */
final class PeriodMetricReader {
  private final MetricQueryService metrics;
  private final TimeRange range;
  private final List<String> notices = new ArrayList<>();

  PeriodMetricReader(MetricQueryService metrics, TimeRange range) {
    this.metrics = metrics;
    this.range = range;
  }

  BigDecimal value(MetricId id) {
    TimeSeriesResult result =
        (TimeSeriesResult)
            metrics.query(new MetricQuery(id, range, TimeGrain.TOTAL, Set.of(), null));
    result.notices().forEach(n -> notices.add(id.value() + ": " + n));
    List<MetricPoint> points = result.series().stream().flatMap(s -> s.points().stream()).toList();
    if (points.size() != 1) {
      if (points.isEmpty()) return null;
      throw new IllegalStateException("Expected a single period aggregate for " + id.value());
    }
    return points.getFirst().value();
  }

  List<String> notices() {
    return List.copyOf(notices);
  }
}
