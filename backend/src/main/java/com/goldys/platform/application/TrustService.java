package com.goldys.platform.application;

import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.ResolutionDetail;
import com.goldys.platform.semantic.ResolutionState;
import com.goldys.platform.semantic.ResolutionStateQuery;
import com.goldys.platform.semantic.SourceValue;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Derives a metric's trust state and freshness from its resolution history and connectors. */
@Service
public class TrustService implements TrustQuery {
  private final ResolutionStateQuery resolution;
  private final ConnectorHealthQuery connectors;
  private final FreshnessProperties freshness;
  private final MetricCatalog catalog;
  private final CanonicalDailySalesQuery dailySales;
  private final DailySalesOverrideService dailyOverrides;
  private final ResolutionRuleService rules;

  private static final String SALES_ENTITY_TYPE = "daily_sales";
  private static final String SALES_FIELD_KEY = "daily_sales";

  public TrustService(
      ResolutionStateQuery resolution,
      ConnectorHealthQuery connectors,
      FreshnessProperties freshness,
      MetricCatalog catalog,
      CanonicalDailySalesQuery dailySales,
      DailySalesOverrideService dailyOverrides,
      ResolutionRuleService rules) {
    this.resolution = resolution;
    this.connectors = connectors;
    this.freshness = freshness;
    this.catalog = catalog;
    this.dailySales = dailySales;
    this.dailyOverrides = dailyOverrides;
    this.rules = rules;
  }

  @Override
  public TrustSummary trustFor(MetricId metric, TimeRange range) {
    if ("derived".equals(catalog.definition(metric).sourceDomain())) {
      return derivedTrust(metric, range);
    }
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

  /** A derived metric has no resolution state of its own; trust its base operands instead. */
  private TrustSummary derivedTrust(MetricId metric, TimeRange range) {
    TrustSummary worst = null;
    for (MetricId constituent : catalog.constituents(metric)) {
      TrustSummary part = trustFor(constituent, range);
      worst = worst == null ? part : leastTrusted(worst, part);
    }
    if (worst == null) {
      throw new IllegalStateException("derived metric has no constituents: " + metric.value());
    }
    return worst;
  }

  private static TrustSummary leastTrusted(TrustSummary a, TrustSummary b) {
    if (a.state().ordinal() != b.state().ordinal()) {
      return a.state().ordinal() > b.state().ordinal() ? a : b;
    }
    return a.freshness().ordinal() >= b.freshness().ordinal() ? a : b;
  }

  @Override
  public Provenance provenanceFor(MetricId metric, LocalDate date) {
    requireSalesMetric(metric);
    ResolutionState state =
        resolution.states(metric, date, date).stream()
            .filter(s -> s.date().equals(date))
            .findFirst()
            .orElse(null);
    List<DailySalesView> rows = dailySales.currentDailySalesForDate(date);
    List<SourceValue> sources =
        rows.stream()
            .map(r -> new SourceValue(r.sourceSystem(), valueFor(metric, r), r.recordedAt()))
            .toList();
    List<UUID> rawRecordIds = dailySales.rawRecordIdsForDate(date);
    BigDecimal resolvedValue = resolvedValueFor(metric, state, rows);
    TrustSummary trust = trustFor(metric, new TimeRange(date, date, Calendar.CALENDAR));
    return new Provenance(
        metric, date, resolvedValue, trust, sources, resolutionDetail(date, state), rawRecordIds);
  }

  private static void requireSalesMetric(MetricId metric) {
    if (metric != MetricId.SALES_GROSS
        && metric != MetricId.SALES_NET
        && metric != MetricId.SALES_GST) {
      throw new IllegalArgumentException("drill-down not yet available for " + metric.value());
    }
  }

  private static BigDecimal valueFor(MetricId metric, DailySalesView row) {
    return switch (metric) {
      case SALES_GROSS -> row.totalSales();
      case SALES_NET -> row.netTotal();
      case SALES_GST -> row.gstTotal();
      default ->
          throw new IllegalArgumentException("drill-down not yet available for " + metric.value());
    };
  }

  /** The value surfaced to operators: the winning source's figure, or null while unresolved. */
  private static BigDecimal resolvedValueFor(
      MetricId metric, ResolutionState state, List<DailySalesView> rows) {
    if (state == null) {
      return null;
    }
    if ("override".equals(state.resolutionType())
        || "rule".equals(state.resolutionType())
        || "single".equals(state.resolutionType())) {
      String authoritative = state.authoritativeSource();
      return rows.stream()
          .filter(r -> r.sourceSystem().equals(authoritative))
          .findFirst()
          .map(r -> valueFor(metric, r))
          .orElse(null);
    }
    if ("agreed".equals(state.resolutionType())) {
      return rows.stream().findFirst().map(r -> valueFor(metric, r)).orElse(null);
    }
    return null; // "conflict" carries no resolved total
  }

  private ResolutionDetail resolutionDetail(LocalDate date, ResolutionState state) {
    if (state == null) {
      return new ResolutionDetail(null, null, "no resolution for this date", null, null);
    }
    if ("override".equals(state.resolutionType())) {
      return dailyOverrides
          .latestFor(date)
          .map(
              o ->
                  new ResolutionDetail(
                      "override",
                      o.authoritativeSource(),
                      o.reason(),
                      o.actorEmail(),
                      o.recordedAt()))
          .orElseGet(() -> synthesized(state));
    }
    if ("rule".equals(state.resolutionType())) {
      return rules
          .currentView(SALES_ENTITY_TYPE, SALES_FIELD_KEY)
          .map(
              r ->
                  new ResolutionDetail(
                      "rule",
                      state.authoritativeSource(),
                      r.strategy(),
                      r.updatedBy(),
                      r.updatedAt()))
          .orElseGet(() -> synthesized(state));
    }
    return synthesized(state);
  }

  private static ResolutionDetail synthesized(ResolutionState state) {
    String reason =
        switch (state.resolutionType()) {
          case "override" -> "manually overridden";
          case "rule" -> "resolved by standing rule";
          case "agreed" -> "sources agree within tolerance";
          case "conflict" -> "conflicting sources unresolved";
          case "single" -> "single source (no data from the other source)";
          default -> state.resolutionType();
        };
    return new ResolutionDetail(
        state.resolutionType(), state.authoritativeSource(), reason, null, state.resolvedAt());
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
      case "single" -> TrustState.SINGLE_SOURCE;
      default -> TrustState.SINGLE_SOURCE;
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
    if (domainHealth.stream()
        .anyMatch(c -> "FAILED".equals(c.status()) || "PARTIAL".equals(c.status()))) {
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
