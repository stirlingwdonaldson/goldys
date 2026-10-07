package com.goldys.platform.semantic.catalog;

import java.util.List;

/** A metric query result: a value shape plus provenance. */
public sealed interface MetricResult permits TimeSeriesResult, RankedListResult {
  MetricId metric();

  /** Runtime caveats to surface to the user (e.g. "2 days unresolved"). */
  List<String> notices();

  MetricProvenance provenance();
}
