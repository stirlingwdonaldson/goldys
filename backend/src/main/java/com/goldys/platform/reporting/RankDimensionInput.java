package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricId;
import java.time.LocalDate;
import java.util.Objects;

/** Input for {@link ToolId#RANK_DIMENSION}: rank a dimension's groups by a metric. */
public record RankDimensionInput(
    MetricId metric, Dimension dimension, LocalDate startDate, LocalDate endDate, int limit)
    implements ToolInput {
  public RankDimensionInput {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(dimension, "dimension");
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
