package com.goldys.platform.semantic.catalog;

import java.util.List;

/** A metric query result: a value shape plus provenance. */
public sealed interface MetricResult permits TimeSeriesResult, RankedListResult {
  MetricId metric();

  /** Runtime caveats to surface to the user (e.g. "2 days unresolved"). */
  List<String> notices();

  MetricProvenance provenance();

  /** Returns a copy of this result carrying {@code provenance} instead of its own. */
  default MetricResult withProvenance(MetricProvenance provenance) {
    return switch (this) {
      case TimeSeriesResult t ->
          new TimeSeriesResult(t.metric(), t.series(), t.notices(), provenance);
      case RankedListResult r ->
          new RankedListResult(r.metric(), r.items(), r.notices(), provenance);
    };
  }
}
