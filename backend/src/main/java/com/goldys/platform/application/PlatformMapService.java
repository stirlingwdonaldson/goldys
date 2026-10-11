package com.goldys.platform.application;

import com.goldys.platform.application.SavedDashboardApplicationService.DashboardDocument;
import com.goldys.platform.application.SavedDashboardApplicationService.DashboardSummary;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.NetworkEdgeDto;
import com.goldys.platform.semantic.NetworkGraphDto;
import com.goldys.platform.semantic.NetworkNodeDto;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricDefinition;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The platform map: how sources feed resolved domains, which metrics read which domain, what
 * derived metrics are built from, and which saved dashboards show them. It answers "CTB is stale:
 * what does that affect?" on the Data health screen.
 *
 * <p>Gated on {@code connectors} READ as a whole, the same operator gate {@link DataQualityService}
 * uses for connector detail. The map carries catalogue metadata (names and dependencies), never
 * metric values, so it does not authorize per metric; a per-metric filter would hand back a
 * silently partial graph, which the permission model forbids.
 *
 * <p>Only {@code source} nodes carry a real tone here. The frontend propagates it downstream, so
 * the propagation rule is unit-tested in one place.
 */
@Service
public class PlatformMapService {
  static final ResourceKey RESOURCE = new ResourceKey("connectors");

  /** A catalogue {@code sourceDomain} that means "computed from other metrics", not a table. */
  static final String DERIVED_DOMAIN = "derived";

  /**
   * Matches {@link DataQualityService}: a domain with no configured threshold goes stale after a
   * day.
   */
  private static final Duration DEFAULT_THRESHOLD = Duration.ofDays(1);

  private static final Map<String, String> SOURCE_LABELS =
      Map.of(
          "LIGHTSPEED", "Lightspeed",
          "CTB", "Cooking the Books",
          "OPENTABLE", "OpenTable",
          "DEPUTY", "Deputy");

  private final PermissionService permissions;
  private final MetricCatalog catalog;
  private final FreshnessProperties freshness;
  private final ConnectorHealthQuery health;
  private final SavedDashboardApplicationService dashboards;
  private final Clock clock;

  @Autowired
  public PlatformMapService(
      PermissionService permissions,
      MetricCatalog catalog,
      FreshnessProperties freshness,
      ConnectorHealthQuery health,
      SavedDashboardApplicationService dashboards) {
    this(permissions, catalog, freshness, health, dashboards, Clock.systemUTC());
  }

  PlatformMapService(
      PermissionService permissions,
      MetricCatalog catalog,
      FreshnessProperties freshness,
      ConnectorHealthQuery health,
      SavedDashboardApplicationService dashboards,
      Clock clock) {
    this.permissions = permissions;
    this.catalog = catalog;
    this.freshness = freshness;
    this.health = health;
    this.dashboards = dashboards;
    this.clock = clock;
  }

  public NetworkGraphDto map(UserRole role, String email) {
    permissions.require(role, RESOURCE, PermissionAction.READ);

    Map<String, NetworkNodeDto> nodes = new LinkedHashMap<>();
    List<NetworkEdgeDto> edges = new ArrayList<>();

    // Sorted so the same catalogue always produces the same graph (and the same cached layout).
    List<MetricId> metrics =
        catalog.ids().stream().sorted(Comparator.comparing(MetricId::value)).toList();
    TreeSet<String> domains = new TreeSet<>();
    for (MetricId id : metrics) {
      String domain = catalog.definition(id).sourceDomain();
      if (!DERIVED_DOMAIN.equals(domain)) {
        domains.add(domain);
      }
    }

    addSources(domains, nodes, edges);

    for (String domain : domains) {
      nodes.put(
          domainId(domain),
          new NetworkNodeDto(
              domainId(domain),
              "domain",
              domainLabel(domain),
              domain,
              "neutral",
              5,
              "domain",
              null));
    }

    for (MetricId id : metrics) {
      MetricDefinition definition = catalog.definition(id);
      boolean derived = !catalog.constituents(id).isEmpty();
      String kind = derived ? "derived-metric" : "metric";
      nodes.put(
          metricId(id),
          new NetworkNodeDto(
              metricId(id),
              kind,
              definition.name(),
              definition.formula(),
              "neutral",
              3,
              kind,
              // The Data health screen opens the metric's provenance from this parameter.
              "/data-health?metric=" + id.value()));

      if (derived) {
        catalog.constituents(id).stream()
            .sorted(Comparator.comparing(MetricId::value))
            .forEach(input -> edges.add(edge(metricId(id), metricId(input), 1, "computed from")));
      } else if (!DERIVED_DOMAIN.equals(definition.sourceDomain())) {
        edges.add(edge(domainId(definition.sourceDomain()), metricId(id), 1, "reads"));
      }

      String permission = "permission:" + definition.requiredPermission();
      nodes.putIfAbsent(
          permission,
          new NetworkNodeDto(
              permission,
              "permission",
              definition.requiredPermission(),
              "Permission resource",
              "neutral",
              2,
              "permission",
              null));
      edges.add(edge(metricId(id), permission, 1, "requires"));
    }

    addDashboards(role, email, nodes, edges);
    return new NetworkGraphDto(List.copyOf(nodes.values()), edges);
  }

  private void addSources(
      TreeSet<String> domains, Map<String, NetworkNodeDto> nodes, List<NetworkEdgeDto> edges) {
    // source system -> the domains it feeds, from config rather than a hand-kept table.
    Map<String, List<String>> fed = new TreeMap<>();
    for (String domain : domains) {
      for (String source : freshness.sources().getOrDefault(domain, List.of())) {
        fed.computeIfAbsent(source, s -> new ArrayList<>()).add(domain);
      }
    }
    List<ConnectorHealth> connectors = health.health();
    Instant now = clock.instant();
    fed.forEach(
        (source, fedDomains) -> {
          String tone = sourceTone(source, fedDomains, connectors, now);
          nodes.put(
              sourceId(source),
              new NetworkNodeDto(
                  sourceId(source),
                  "source",
                  SOURCE_LABELS.getOrDefault(source, source),
                  toneSubtitle(tone),
                  tone,
                  6,
                  "source",
                  "/logs"));
          for (String domain : fedDomains) {
            edges.add(edge(sourceId(source), domainId(domain), 1, "feeds"));
          }
        });
  }

  /**
   * A source's tone from its connectors' last runs: any failed or partial run is {@code fail}; no
   * run at all is {@code missing}; a run older than the tightest threshold among the domains it
   * feeds is {@code warn}. The same rules {@link DataQualityService} uses to blame a connector.
   */
  private String sourceTone(
      String source, List<String> fedDomains, List<ConnectorHealth> connectors, Instant now) {
    List<ConnectorHealth> runs =
        connectors.stream().filter(c -> source.equals(c.source())).toList();
    if (runs.stream().anyMatch(c -> "FAILED".equals(c.status()) || "PARTIAL".equals(c.status()))) {
      return "fail";
    }
    Optional<Instant> latest =
        runs.stream()
            .map(ConnectorHealth::lastRunAt)
            .filter(t -> t != null)
            .max(Instant::compareTo);
    if (latest.isEmpty()) {
      return "missing";
    }
    Duration threshold =
        fedDomains.stream()
            .map(d -> freshness.thresholds().getOrDefault(d, DEFAULT_THRESHOLD))
            .min(Duration::compareTo)
            .orElse(DEFAULT_THRESHOLD);
    return now.isAfter(latest.get().plus(threshold)) ? "warn" : "ok";
  }

  private static String toneSubtitle(String tone) {
    return switch (tone) {
      case "fail" -> "Last run failed";
      case "missing" -> "Never run";
      case "warn" -> "Stale";
      default -> "Up to date";
    };
  }

  private void addDashboards(
      UserRole role, String email, Map<String, NetworkNodeDto> nodes, List<NetworkEdgeDto> edges) {
    for (DashboardSummary summary : dashboards.list(role, email)) {
      DashboardDocument document = dashboards.get(role, email, summary.id());
      // How many of the dashboard's queries use each metric, so a dashboard built around one
      // metric pulls toward it harder than one that mentions it once.
      Map<MetricId, Integer> uses = new TreeMap<>(Comparator.comparing(MetricId::value));
      int total = 0;
      for (SavedWidget widget : document.widgets()) {
        for (MetricQuery query : widget.queries()) {
          uses.merge(query.metric(), 1, Integer::sum);
          total++;
        }
      }
      String id = "dashboard:" + summary.id();
      nodes.put(
          id,
          new NetworkNodeDto(
              id,
              "dashboard",
              summary.title(),
              document.widgets().size() + " widgets",
              "neutral",
              4,
              "dashboard",
              "/dashboards/" + summary.id()));
      for (Map.Entry<MetricId, Integer> use : uses.entrySet()) {
        if (nodes.containsKey(metricId(use.getKey()))) {
          edges.add(edge(id, metricId(use.getKey()), (double) use.getValue() / total, "shows"));
        }
      }
    }
  }

  static String sourceId(String source) {
    return "source:" + source;
  }

  static String domainId(String domain) {
    return "domain:" + domain;
  }

  static String metricId(MetricId id) {
    return "metric:" + id.value();
  }

  private static NetworkEdgeDto edge(String source, String target, double weight, String label) {
    return new NetworkEdgeDto(source, target, weight, label);
  }

  /** {@code resolved_labour_day} → "Labour"; {@code resolved_daily_sales} → "Daily sales". */
  static String domainLabel(String domain) {
    String trimmed = domain.replaceFirst("^resolved_", "").replaceFirst("_day$", "");
    String words = trimmed.replace('_', ' ');
    return words.isEmpty()
        ? domain
        : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
  }
}
