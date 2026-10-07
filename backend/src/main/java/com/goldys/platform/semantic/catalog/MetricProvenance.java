package com.goldys.platform.semantic.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Provenance for a metric result: identity, range, freshness, and missing periods. */
public record MetricProvenance(
    MetricId metric,
    String definitionVersion,
    TimeRange range,
    TimeGrain grain,
    String sourceDomain,
    Instant dataFreshness,
    List<LocalDate> missingPeriods,
    String calculationVersion) {
  public MetricProvenance {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(definitionVersion, "definitionVersion");
    Objects.requireNonNull(range, "range");
    Objects.requireNonNull(grain, "grain");
    missingPeriods = missingPeriods == null ? List.of() : List.copyOf(missingPeriods);
    Objects.requireNonNull(calculationVersion, "calculationVersion");
  }
}
