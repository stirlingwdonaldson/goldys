package com.goldys.platform.application;

import com.goldys.platform.application.ConnectorApplicationService.ConnectorStatus;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Connects an untrusted metric to the connector(s) responsible for its missing or stale data, so
 * operators can jump from a data-quality flag to the ingestion/log detail that explains it.
 *
 * <p>The result is gated on {@code connectors} READ — the same boundary {@link
 * ConnectorApplicationService} enforces — so only authorised operators see the ingestion/log detail
 * (source id + failure). Ordinary staff keep the compact {@code TrustSummary}, which carries no
 * connector diagnostics.
 */
@Service
public class DataQualityService {
  private final TrustQuery trust;
  private final ConnectorHealthQuery health;
  private final FreshnessProperties freshness;
  private final MetricCatalog catalog;
  private final ConnectorApplicationService connectors;

  public DataQualityService(
      TrustQuery trust,
      ConnectorHealthQuery health,
      FreshnessProperties freshness,
      MetricCatalog catalog,
      ConnectorApplicationService connectors) {
    this.trust = trust;
    this.health = health;
    this.freshness = freshness;
    this.catalog = catalog;
    this.connectors = connectors;
  }

  /**
   * The operator-facing {@link ConnectorStatus} of the domain source(s) behind an untrusted metric,
   * or empty when the metric is trusted or has no attributable source.
   *
   * @throws com.goldys.platform.auth.AccessDeniedException when the role lacks {@code connectors}
   *     READ, before any trust work is done.
   */
  public List<ConnectorStatus> responsibleConnectors(
      UserRole role, MetricId metric, TimeRange range) {
    // The connectors gate: only operators may see ingestion/log detail.
    List<ConnectorStatus> statuses = connectors.connectors(role);

    TrustSummary summary = trust.trustFor(metric, range);
    if (!needsAttention(summary)) {
      return List.of();
    }

    String domain = catalog.definition(metric).sourceDomain();
    Set<String> responsible = responsibleSources(domain, health.health(), Instant.now());
    if (responsible.isEmpty()) {
      return List.of();
    }

    return statuses.stream().filter(s -> responsible.contains(s.source())).toList();
  }

  /**
   * A metric needs operator attention when it is incomplete, missing, or its data has failed/stale.
   */
  private static boolean needsAttention(TrustSummary summary) {
    TrustState state = summary.state();
    FreshnessState freshness = summary.freshness();
    return state == TrustState.INCOMPLETE
        || state == TrustState.NOT_RECEIVED
        || freshness == FreshnessState.SOURCE_FAILURE
        || freshness == FreshnessState.STALE;
  }

  /** The domain's source-system ids whose connector has failed or gone stale. */
  private Set<String> responsibleSources(String domain, List<ConnectorHealth> health, Instant now) {
    List<String> sources = freshness.sources().getOrDefault(domain, List.of());
    Duration threshold = freshness.thresholds().getOrDefault(domain, Duration.ofDays(1));
    return health.stream()
        .filter(c -> sources.contains(c.source()))
        .filter(
            c ->
                "FAILED".equals(c.status())
                    || "PARTIAL".equals(c.status())
                    || stale(c, threshold, now))
        .map(ConnectorHealth::source)
        .collect(Collectors.toSet());
  }

  private static boolean stale(ConnectorHealth c, Duration threshold, Instant now) {
    return c.lastRunAt() == null || now.isAfter(c.lastRunAt().plus(threshold));
  }
}
