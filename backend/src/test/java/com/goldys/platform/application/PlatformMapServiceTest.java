package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goldys.platform.application.SavedDashboardApplicationService.DashboardDocument;
import com.goldys.platform.application.SavedDashboardApplicationService.DashboardSummary;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.FreshnessProperties;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.NetworkEdgeDto;
import com.goldys.platform.semantic.NetworkGraphDto;
import com.goldys.platform.semantic.NetworkNodeDto;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlatformMapServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
  private static final String EMAIL = "owner@example.com";

  private final PermissionService permissions = mock(PermissionService.class);
  private final ConnectorHealthQuery health = mock(ConnectorHealthQuery.class);
  private final SavedDashboardApplicationService dashboards =
      mock(SavedDashboardApplicationService.class);
  private final MetricCatalog catalog = new MetricCatalog();

  private final FreshnessProperties freshness =
      new FreshnessProperties(
          Map.of("resolved_daily_sales", Duration.ofHours(2)),
          Map.of(
              "resolved_daily_sales", List.of("LIGHTSPEED", "CTB"),
              "resolved_product_sales", List.of("LIGHTSPEED", "CTB"),
              "resolved_reservation_day", List.of("OPENTABLE"),
              "resolved_labour_day", List.of("DEPUTY"),
              "resolved_inventory_day", List.of("CTB")));

  private PlatformMapService service() {
    return new PlatformMapService(
        permissions, catalog, freshness, health, dashboards, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static Map<String, NetworkNodeDto> byId(NetworkGraphDto graph) {
    return graph.nodes().stream()
        .collect(Collectors.toMap(NetworkNodeDto::id, Function.identity()));
  }

  private static boolean linked(NetworkGraphDto graph, String a, String b) {
    return graph.edges().stream()
        .anyMatch(
            e ->
                (e.source().equals(a) && e.target().equals(b))
                    || (e.source().equals(b) && e.target().equals(a)));
  }

  private static MetricQuery query(MetricId metric) {
    LocalDate day = LocalDate.of(2026, 10, 1);
    return new MetricQuery(
        metric, new TimeRange(day, day, Calendar.CALENDAR), TimeGrain.DAY, null, null);
  }

  @Test
  void deniesWithoutConnectorsReadBeforeDoingAnyWork() {
    doThrow(AccessDeniedException.forResource("connectors"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service().map(role(), EMAIL))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(health, dashboards);
  }

  @Test
  void requiresReadOnTheConnectorsResource() {
    when(health.health()).thenReturn(List.of());
    service().map(role(), EMAIL);
    verify(permissions).require(role(), new ResourceKey("connectors"), PermissionAction.READ);
  }

  @Test
  void drawsSourcesDomainsMetricsAndPermissions() {
    when(health.health()).thenReturn(List.of());

    NetworkGraphDto graph = service().map(role(), EMAIL);
    Map<String, NetworkNodeDto> nodes = byId(graph);

    assertThat(nodes.get("source:CTB").label()).isEqualTo("Cooking the Books");
    assertThat(nodes.get("domain:resolved_daily_sales").label()).isEqualTo("Daily sales");
    assertThat(nodes.get("metric:sales.gross").kind()).isEqualTo("metric");
    assertThat(nodes.get("metric:labour.cost_per_cover").kind()).isEqualTo("derived-metric");
    assertThat(nodes).doesNotContainKey("domain:derived");
    assertThat(nodes.get("permission:reconciliation.sales").kind()).isEqualTo("permission");

    assertThat(linked(graph, "source:CTB", "domain:resolved_daily_sales")).isTrue();
    assertThat(linked(graph, "domain:resolved_daily_sales", "metric:sales.gross")).isTrue();
    assertThat(linked(graph, "metric:labour.cost_per_cover", "metric:labour.cost")).isTrue();
    assertThat(linked(graph, "metric:labour.cost_per_cover", "metric:reservations.covers"))
        .isTrue();
    assertThat(linked(graph, "metric:sales.gross", "permission:reconciliation.sales")).isTrue();
  }

  @Test
  void onlySourceNodesCarryATone() {
    when(health.health()).thenReturn(List.of());

    NetworkGraphDto graph = service().map(role(), EMAIL);

    assertThat(graph.nodes())
        .filteredOn(n -> !n.kind().equals("source"))
        .allMatch(n -> n.tone().equals("neutral"));
  }

  @Test
  void sourceToneFollowsConnectorHealth() {
    when(health.health())
        .thenReturn(
            List.of(
                // CTB: one connector fine, one partial → failing overall.
                new ConnectorHealth("CTB", "ctb-revenue", NOW.minusSeconds(60), "SUCCESS"),
                new ConnectorHealth("CTB", "ctb-invoices", NOW.minusSeconds(60), "PARTIAL"),
                // Lightspeed feeds daily sales (2h threshold): 3h old → stale.
                new ConnectorHealth(
                    "LIGHTSPEED", "lightspeed", NOW.minusSeconds(3 * 3600), "SUCCESS"),
                // OpenTable: 3h old but its domain has the 1-day default → fine.
                new ConnectorHealth(
                    "OPENTABLE", "opentable", NOW.minusSeconds(3 * 3600), "SUCCESS")));
    // Deputy has never run.

    Map<String, NetworkNodeDto> nodes = byId(service().map(role(), EMAIL));

    assertThat(nodes.get("source:CTB").tone()).isEqualTo("fail");
    assertThat(nodes.get("source:LIGHTSPEED").tone()).isEqualTo("warn");
    assertThat(nodes.get("source:OPENTABLE").tone()).isEqualTo("ok");
    assertThat(nodes.get("source:DEPUTY").tone()).isEqualTo("missing");
  }

  @Test
  void linksDashboardsToTheirMetricsWeightedByUse() {
    when(health.health()).thenReturn(List.of());
    UUID id = UUID.randomUUID();
    DashboardSummary summary = mock(DashboardSummary.class);
    when(summary.id()).thenReturn(id);
    when(summary.title()).thenReturn("Weekly trading");
    DashboardDocument document = mock(DashboardDocument.class);
    WidgetLayout layout = new WidgetLayout(6, 2);
    when(document.widgets())
        .thenReturn(
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(query(MetricId.SALES_GROSS), query(MetricId.SALES_GROSS)),
                    layout),
                new SavedWidget("w2", "stat", List.of(query(MetricId.LABOUR_COST)), layout)));
    when(dashboards.list(role(), EMAIL)).thenReturn(List.of(summary));
    when(dashboards.get(role(), EMAIL, id)).thenReturn(document);

    NetworkGraphDto graph = service().map(role(), EMAIL);

    NetworkNodeDto node = byId(graph).get("dashboard:" + id);
    assertThat(node.label()).isEqualTo("Weekly trading");
    assertThat(node.href()).isEqualTo("/dashboards/" + id);
    Map<String, Double> weights =
        graph.edges().stream()
            .filter(e -> e.source().equals("dashboard:" + id))
            .collect(Collectors.toMap(NetworkEdgeDto::target, NetworkEdgeDto::weight));
    assertThat(weights.get("metric:sales.gross")).isCloseTo(2.0 / 3, within());
    assertThat(weights.get("metric:labour.cost")).isCloseTo(1.0 / 3, within());
  }

  @Test
  void labelsDomainsFromTheirTableNames() {
    assertThat(PlatformMapService.domainLabel("resolved_labour_day")).isEqualTo("Labour");
    assertThat(PlatformMapService.domainLabel("resolved_product_sales")).isEqualTo("Product sales");
    assertThat(PlatformMapService.domainLabel("accounting")).isEqualTo("Accounting");
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-9);
  }
}
