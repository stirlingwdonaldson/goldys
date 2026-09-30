package com.goldys.platform.reconciliation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure evaluation of a single rule against per-source metrics. No I/O, no permissions — the
 * reconciliation services call this after manual overrides and agreement have already been
 * considered.
 */
final class RuleEvaluator {

  private RuleEvaluator() {}

  /** Picks the authoritative source, or empty when the rule leaves the unit unresolved. */
  static Optional<String> resolve(ResolutionRule rule, List<SourceMetric> sources) {
    if (sources.isEmpty()) {
      return Optional.empty();
    }
    return switch (rule.strategy()) {
      case "priority" -> priority(rule.sourcePriority(), sources);
      case "manual" -> Optional.empty();
      case "custom" -> custom(rule.customLogic(), sources);
      default -> Optional.empty();
    };
  }

  private static Optional<String> priority(List<String> order, List<SourceMetric> sources) {
    if (order == null) {
      return Optional.empty();
    }
    for (String source : order) {
      boolean present = sources.stream().anyMatch(s -> s.sourceSystem().equals(source));
      if (present) {
        return Optional.of(source);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> custom(String logic, List<SourceMetric> sources) {
    if (logic == null) {
      return Optional.empty();
    }
    return switch (logic) {
      case "flag" -> Optional.empty();
      case "highest" -> pick(sources, Comparator.comparing(SourceMetric::metric));
      case "lowest" -> pick(sources, Comparator.comparing(SourceMetric::metric).reversed());
      case "newest" -> pick(sources, Comparator.comparing(SourceMetric::recordedAt));
      default -> Optional.empty();
    };
  }

  private static Optional<String> pick(List<SourceMetric> sources, Comparator<SourceMetric> by) {
    return sources.stream().filter(s -> s.metric() != null).max(by).map(SourceMetric::sourceSystem);
  }
}
