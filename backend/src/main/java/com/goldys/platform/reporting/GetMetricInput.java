package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeGrain;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Input for {@link ToolId#GET_METRIC}. */
public record GetMetricInput(
    MetricId metric,
    LocalDate startDate,
    LocalDate endDate,
    TimeGrain grain,
    Set<Dimension> dimensions)
    implements ToolInput {
  public GetMetricInput {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    Objects.requireNonNull(grain, "grain");
    dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }
}
