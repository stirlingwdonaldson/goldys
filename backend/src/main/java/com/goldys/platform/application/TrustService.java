package com.goldys.platform.application;

import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Derives a metric's trust state and freshness from its resolution history and connectors. */
@Service
public class TrustService implements TrustQuery {
  private final ResolutionStateQuery resolution;
  private final ConnectorHealthQuery connectors;
  private final FreshnessProperties freshness;
  private final MetricCatalog catalog;

  public TrustService(
      ResolutionStateQuery resolution,
      ConnectorHealthQuery connectors,
      FreshnessProperties freshness,
      MetricCatalog catalog) {
    this.resolution = resolution;
    this.connectors = connectors;
    this.freshness = freshness;
    this.catalog = catalog;
  }

  @Override
  public TrustSummary trustFor(MetricId metric, TimeRange range) {
    List<ResolutionState> states = resolution.states(metric, range.from(), range.to());
    String domain = catalog.definition(metric).sourceDomain();
    Duration threshold = freshness.thresholds().getOrDefault(domain, Duration.ofDays(1));
    List<String> sources = freshness.sources().getOrDefault(domain, List.of());
    TrustState state = aggregate(states, range);
    FreshnessState fresh = freshnessState(states, connectors.health(), sources, threshold);
    String authoritative = states.isEmpty() ? null : states.get(0).authoritativeSource();
    Instant resolvedAt =
        states.stream()
            .map(ResolutionState::resolvedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);
    Instant lastIngest = lastIngestionFor(sources, connectors.health());
    return new TrustSummary(state, fresh, authoritative, resolvedAt, lastIngest, threshold);
  }

  @Override
  public Provenance provenanceFor(MetricId metric, LocalDate date) {
    // Drill-down assembly is deferred to Task 7.
    throw new UnsupportedOperationException("provenanceFor is not implemented yet");
  }

  private static TrustState aggregate(List<ResolutionState> states, TimeRange range) {
    if (states.isEmpty()) return TrustState.NOT_RECEIVED;
    Map<LocalDate, TrustState> perDate = new LinkedHashMap<>();
    for (ResolutionState s : states) {
      perDate.merge(s.date(), perDate(s.resolutionType()), TrustService::leastTrusted);
    }
    TrustState worst = TrustState.VERIFIED;
    for (TrustState t : perDate.values()) worst = leastTrusted(worst, t);
    long days = range.to().toEpochDay() - range.from().toEpochDay() + 1;
    return perDate.size() < days ? TrustState.INCOMPLETE : worst;
  }

  private static TrustState perDate(String resolutionType) {
    return switch (resolutionType) {
      case "agreed" -> TrustState.VERIFIED;
      case "rule" -> TrustState.RESOLVED_BY_RULE;
      case "override" -> TrustState.MANUALLY_OVERRIDDEN;
      case "conflict" -> TrustState.CONFLICTED;
      default -> TrustState.SINGLE_SOURCE; // "missing" with one source
    };
  }

  private static TrustState leastTrusted(TrustState a, TrustState b) {
    // VERIFIED is most trusted; NOT_RECEIVED least (ranked by enum ordinal, reversed).
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  private static FreshnessState freshnessState(
      List<ResolutionState> states,
      List<ConnectorHealth> health,
      List<String> sources,
      Duration threshold) {
    if (states.isEmpty()) return FreshnessState.UNKNOWN;
    List<ConnectorHealth> domainHealth =
        health.stream().filter(c -> sources.contains(c.source())).toList();
    if (domainHealth.isEmpty()) return FreshnessState.UNKNOWN;
    if (domainHealth.stream().anyMatch(c -> "FAILED".equals(c.status()))) {
      return FreshnessState.SOURCE_FAILURE;
    }
    Instant freshest =
        domainHealth.stream()
            .map(ConnectorHealth::lastRunAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    if (freshest == null) return FreshnessState.UNKNOWN;
    Instant now = Instant.now();
    return now.isAfter(freshest.plus(threshold)) ? FreshnessState.STALE : FreshnessState.FRESH;
  }

  private static Instant lastIngestionFor(List<String> sources, List<ConnectorHealth> health) {
    return health.stream()
        .filter(c -> sources.contains(c.source()))
        .map(ConnectorHealth::lastRunAt)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }
}
