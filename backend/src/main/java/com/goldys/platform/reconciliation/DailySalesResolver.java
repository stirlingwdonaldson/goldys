package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Pure daily-sales resolution: override → agreement (one-cent tolerance) → rule → unresolved.
 * No I/O and no permissions; the projector and any future consumer share this one implementation. */
final class DailySalesResolver {
  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  private DailySalesResolver() {}

  record Result(String resolutionType, String authoritativeSource, BigDecimal totalSales) {
    boolean hasConflict() {
      return "conflict".equals(resolutionType) || "missing".equals(resolutionType);
    }
  }

  static Optional<Result> resolve(
      List<SourceTotal> sources, Optional<String> overrideSource, Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return sources.stream()
          .filter(s -> s.sourceSystem().equals(overrideSource.get()))
          .findFirst()
          .map(s -> new Result("override", s.sourceSystem(), s.totalSales()));
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      return Optional.of(new Result("agreed", "agreed", sources.get(0).totalSales()));
    }
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        return sources.stream()
            .filter(s -> s.sourceSystem().equals(chosen.get()))
            .findFirst()
            .map(s -> new Result("rule", s.sourceSystem(), s.totalSales()));
      }
    }
    return Optional.of(new Result(status, null, null));
  }

  static String classify(List<SourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
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
