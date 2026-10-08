package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Pure daily-sales resolution: override → agreement (one-cent tolerance) → rule → unresolved. No
 * I/O and no permissions; the projector and any future consumer share this one implementation.
 */
final class DailySalesResolver {
  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  private DailySalesResolver() {}

  record Result(
      String resolutionType,
      String authoritativeSource,
      BigDecimal totalSales,
      BigDecimal gst,
      BigDecimal net) {
    boolean hasConflict() {
      return "conflict".equals(resolutionType);
    }
  }

  static Optional<Result> resolve(
      List<SourceTotal> sources, Optional<String> overrideSource, Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return sources.stream()
          .filter(s -> s.sourceSystem().equals(overrideSource.get()))
          .findFirst()
          .map(s -> result("override", s.sourceSystem(), s));
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("single".equals(status)) {
      // One source reported; resolve to it (SINGLE_SOURCE), never flagged as missing.
      SourceTotal s = sources.get(0);
      return Optional.of(result("single", s.sourceSystem(), s));
    }
    if ("agreed".equals(status)) {
      SourceTotal s = sources.get(0);
      return Optional.of(result("agreed", "agreed", s));
    }
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        return sources.stream()
            .filter(s -> s.sourceSystem().equals(chosen.get()))
            .findFirst()
            .map(s -> result("rule", s.sourceSystem(), s));
      }
    }
    return Optional.of(new Result(status, null, null, null, null));
  }

  private static Result result(String type, String source, SourceTotal s) {
    return new Result(type, source, s.totalSales(), s.gstTotal(), s.netTotal());
  }

  static String classify(List<SourceTotal> sources) {
    if (sources.size() < 2) {
      return "single";
    }
    BigDecimal first = sources.get(0).totalSales();
    boolean allAgree = sources.stream().allMatch(s -> withinCent(first, s.totalSales()));
    return allAgree ? "agreed" : "conflict";
  }

  static boolean withinCent(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.subtract(b).abs().compareTo(ONE_CENT) <= 0;
  }

  private static List<SourceMetric> toMetrics(List<SourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.totalSales(), s.recordedAt()))
        .toList();
  }
}
