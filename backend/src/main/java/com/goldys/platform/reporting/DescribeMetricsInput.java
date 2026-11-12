package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.MetricId;
import java.util.Set;

/** Empty selection discovers accessible definitions; explicit selections are denied atomically. */
public record DescribeMetricsInput(Set<MetricId> metrics) implements ToolInput {
  public DescribeMetricsInput {
    metrics = metrics == null ? Set.of() : Set.copyOf(metrics);
  }
}
