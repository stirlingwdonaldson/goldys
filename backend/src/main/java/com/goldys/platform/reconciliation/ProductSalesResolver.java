package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Pure product-sales resolution: override → single source (trusted) → agreement (one-cent tolerance
 * on amount, exact quantity) → rule (evaluated on quantity_sold) → unresolved. No I/O and no
 * permissions; the projector and any future consumer share this one implementation.
 *
 * <p>Unlike daily sales, a product reported by only one source is trusted as-is rather than
 * flagged: sources do not overlap 1:1 for product-level data.
 */
final class ProductSalesResolver {
  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

  private ProductSalesResolver() {}

  record Result(
      String resolutionType,
      String authoritativeSource,
      BigDecimal quantitySold,
      BigDecimal amount) {
    boolean hasConflict() {
      return "conflict".equals(resolutionType);
    }
  }

  static Optional<Result> resolve(
      List<ProductSourceTotal> sources,
      Optional<String> overrideSource,
      Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return sources.stream()
          .filter(s -> s.sourceSystem().equals(overrideSource.get()))
          .findFirst()
          .map(s -> new Result("override", s.sourceSystem(), s.quantitySold(), s.amount()));
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    if (sources.size() == 1) {
      ProductSourceTotal only = sources.get(0);
      return Optional.of(
          new Result("single", only.sourceSystem(), only.quantitySold(), only.amount()));
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      ProductSourceTotal first = sources.get(0);
      return Optional.of(new Result("agreed", "agreed", first.quantitySold(), first.amount()));
    }
    if (rule.isPresent()) {
      Optional<String> chosen = RuleEvaluator.resolve(rule.get(), toMetrics(sources));
      if (chosen.isPresent()) {
        return sources.stream()
            .filter(s -> s.sourceSystem().equals(chosen.get()))
            .findFirst()
            .map(s -> new Result("rule", s.sourceSystem(), s.quantitySold(), s.amount()));
      }
    }
    return Optional.of(new Result("conflict", null, null, null));
  }

  /** Classifies two or more sources as "agreed" (within tolerance) or "conflict". */
  static String classify(List<ProductSourceTotal> sources) {
    if (sources.size() < 2) {
      return "single";
    }
    ProductSourceTotal first = sources.get(0);
    boolean agree =
        sources.stream()
            .allMatch(
                s ->
                    first.quantitySold().compareTo(s.quantitySold()) == 0
                        && withinCent(first.amount(), s.amount()));
    return agree ? "agreed" : "conflict";
  }

  static boolean withinCent(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.subtract(b).abs().compareTo(ONE_CENT) <= 0;
  }

  private static List<SourceMetric> toMetrics(List<ProductSourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.quantitySold(), s.recordedAt()))
        .toList();
  }
}
