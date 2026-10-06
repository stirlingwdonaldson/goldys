package com.goldys.platform.reconciliation;

import java.util.List;
import java.util.Optional;

/** Pure product-sales resolution: override → agreement (zero tolerance on quantity AND amount) →
 * rule (evaluated on quantity_sold) → unresolved. Returns the exception status, or empty when the
 * pair is resolved or has no data. */
final class ProductSalesResolver {
  private ProductSalesResolver() {}

  static Optional<String> resolve(
      List<ProductSourceTotal> sources,
      Optional<String> overrideSource,
      Optional<ResolutionRule> rule) {
    if (overrideSource.isPresent()) {
      return Optional.empty();
    }
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    String status = classify(sources);
    if ("agreed".equals(status)) {
      return Optional.empty();
    }
    if (rule.isPresent() && RuleEvaluator.resolve(rule.get(), toMetrics(sources)).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(status);
  }

  static String classify(List<ProductSourceTotal> sources) {
    if (sources.size() < 2) {
      return "missing";
    }
    ProductSourceTotal first = sources.get(0);
    boolean agree =
        sources.stream()
            .allMatch(
                s ->
                    first.quantitySold().compareTo(s.quantitySold()) == 0
                        && first.amount().compareTo(s.amount()) == 0);
    return agree ? "agreed" : "conflict";
  }

  private static List<SourceMetric> toMetrics(List<ProductSourceTotal> sources) {
    return sources.stream()
        .map(s -> new SourceMetric(s.sourceSystem(), s.quantitySold(), s.recordedAt()))
        .toList();
  }
}
