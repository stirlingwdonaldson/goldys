package com.goldys.platform.semantic.catalog;

import java.time.LocalDate;
import java.util.List;

/**
 * Coverage of the requested calendar grid; receipt presence does not prove trading completeness.
 */
public record MetricCoverage(
    LocalDate from,
    LocalDate to,
    List<LocalDate> observedDates,
    List<LocalDate> unresolvedDates,
    List<LocalDate> absentDates,
    boolean expectedTradingPopulationKnown) {
  public MetricCoverage(
      LocalDate from,
      LocalDate to,
      List<LocalDate> observed,
      List<LocalDate> unresolved,
      List<LocalDate> absent) {
    this(from, to, observed, unresolved, absent, false);
  }

  public MetricCoverage {
    observedDates = List.copyOf(observedDates);
    unresolvedDates = List.copyOf(unresolvedDates);
    absentDates = List.copyOf(absentDates);
  }

  /** Calendar dates with an unavailable amount, preserving the legacy provenance meaning. */
  public List<LocalDate> missingDates() {
    return java.util.stream.Stream.concat(unresolvedDates.stream(), absentDates.stream())
        .sorted()
        .toList();
  }

  public List<String> notices() {
    String detail =
        observedDates.size()
            + " observed day(s), "
            + unresolvedDates.size()
            + " unresolved day(s), "
            + absentDates.size()
            + " day(s) without a resolved record";
    return List.of(
        (unresolvedDates.isEmpty() && absentDates.isEmpty()
                ? "Calendar coverage: "
                : "Incomplete subtotal: ")
            + detail
            + "; expected trading population and within-day completeness are unverified");
  }
}
