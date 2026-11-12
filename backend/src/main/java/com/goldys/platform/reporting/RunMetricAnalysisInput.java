package com.goldys.platform.reporting;

import java.util.List;

/** A bounded composition of existing metric queries; every step carries explicit scope. */
public record RunMetricAnalysisInput(List<GetMetricInput> queries) implements ToolInput {
  public RunMetricAnalysisInput {
    if (queries == null || queries.isEmpty() || queries.size() > 6) {
      throw new IllegalArgumentException("An analysis requires 1..6 metric queries");
    }
    queries = List.copyOf(queries);
  }
}
