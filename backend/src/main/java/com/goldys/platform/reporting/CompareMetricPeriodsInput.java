package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeGrain;
import java.time.LocalDate;
import java.util.Objects;

/** Input for {@link ToolId#COMPARE_METRIC_PERIODS}. */
public record CompareMetricPeriodsInput(
    MetricId metric, LocalDate startDate, LocalDate endDate, Comparison comparison, TimeGrain grain)
    implements ToolInput {
  public CompareMetricPeriodsInput {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    Objects.requireNonNull(comparison, "comparison");
    Objects.requireNonNull(grain, "grain");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
  }
}
