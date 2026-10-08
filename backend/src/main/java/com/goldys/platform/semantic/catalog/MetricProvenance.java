package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.TrustSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Provenance for a metric result: identity, range, freshness, missing periods, and trust. */
public record MetricProvenance(
    MetricId metric,
    String definitionVersion,
    TimeRange range,
    TimeGrain grain,
    String sourceDomain,
    Instant dataFreshness,
    List<LocalDate> missingPeriods,
    String calculationVersion,
    TrustSummary trust) {

  /** Convenience constructor for executors, which build provenance without trust. */
  public MetricProvenance(
      MetricId metric,
      String definitionVersion,
      TimeRange range,
      TimeGrain grain,
      String sourceDomain,
      Instant dataFreshness,
      List<LocalDate> missingPeriods,
      String calculationVersion) {
    this(
        metric,
        definitionVersion,
        range,
        grain,
        sourceDomain,
        dataFreshness,
        missingPeriods,
        calculationVersion,
        null);
  }

  public MetricProvenance {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(definitionVersion, "definitionVersion");
    Objects.requireNonNull(range, "range");
    Objects.requireNonNull(grain, "grain");
    missingPeriods = missingPeriods == null ? List.of() : List.copyOf(missingPeriods);
    Objects.requireNonNull(calculationVersion, "calculationVersion");
  }

  /** Returns a copy carrying {@code trust}, with {@code dataFreshness} derived from it. */
  public MetricProvenance withTrust(TrustSummary trust) {
    Objects.requireNonNull(trust, "trust");
    Instant freshness = trust.resolvedAt() != null ? trust.resolvedAt() : trust.lastIngestionAt();
    return new MetricProvenance(
        metric,
        definitionVersion,
        range,
        grain,
        sourceDomain,
        freshness,
        missingPeriods,
        calculationVersion,
        trust);
  }
}
