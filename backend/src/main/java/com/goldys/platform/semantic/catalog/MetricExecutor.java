package com.goldys.platform.semantic.catalog;

import java.util.Set;

/** One explicit, tested implementation of a metric's query path. */
public interface MetricExecutor {
  Set<MetricId> ids();

  MetricResult evaluate(MetricQuery query);
}
