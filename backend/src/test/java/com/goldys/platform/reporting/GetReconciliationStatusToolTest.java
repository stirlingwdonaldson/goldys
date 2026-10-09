package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GetReconciliationStatusToolTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static final Instant LAST_RUN = Instant.parse("2026-10-01T00:00:00Z");

  @Test
  void exposesStatusCapabilityAndEmptyMetricQueries() {
    GetReconciliationStatusTool tool =
        new GetReconciliationStatusTool(
            mock(MetricQueryService.class),
            mock(SalesMetricsQuery.class),
            mock(ProductMetricsQuery.class),
            mock(ConnectorHealthQuery.class));

    assertThat(tool.name()).isEqualTo("get_reconciliation_status");
    assertThat(tool.resource().value()).isEqualTo("reconciliation.status");
    assertThat(tool.inputType()).isEqualTo(GetReconciliationStatusInput.class);
    assertThat(tool.toMetricQueries(new GetReconciliationStatusInput())).isEmpty();
  }

  @Test
  void statusReportsDomainsAndConnectors() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricId metric = ((MetricQuery) inv.getArgument(0)).metric();
              return switch (metric) {
                case SALES_GROSS -> tsResult(metric, missing(3));
                case RESERVATIONS_COVERS -> tsResult(metric, missing(0));
                case LABOUR_COST -> tsResult(metric, missing(1));
                case INVENTORY_PURCHASES -> tsResult(metric, missing(0));
                case PRODUCT_SALES_AMOUNT -> tsResult(metric, missing(2));
                default -> throw new IllegalArgumentException("unexpected metric " + metric);
              };
            });

    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.openConflicts()).thenReturn(2L);

    ProductMetricsQuery product = mock(ProductMetricsQuery.class);
    when(product.openConflicts()).thenReturn(1L);

    ConnectorHealthQuery connectors = mock(ConnectorHealthQuery.class);
    when(connectors.health())
        .thenReturn(
            List.of(new ConnectorHealth("LIGHTSPEED", "lightspeed-products", LAST_RUN, "SUCCESS")));

    GetReconciliationStatusTool tool =
        new GetReconciliationStatusTool(metrics, sales, product, connectors);
    ToolResult result = tool.execute(new GetReconciliationStatusInput(), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    assertThat(result.provenance()).isEmpty();
    assertThat(result.relatedMetrics()).isEmpty();
    assertThat(result.notices()).isEmpty();

    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.title()).isEqualTo("Reconciliation status");
    assertThat(widget.columns())
        .containsExactly(
            new Column("domain", "Domain", null), new Column("status", "Status", null));

    assertThat(widget.rows())
        .contains(
            row("Sales", "2 open conflicts"),
            row("Product", "1 open conflicts"),
            row("Sales", "3 unresolved days"),
            row("Reservations", "0 unresolved days"),
            row("Labour", "1 unresolved days"),
            row("Inventory", "0 unresolved days"),
            row("Product", "2 unresolved days"),
            row("LIGHTSPEED", "lightspeed-products: last run " + LAST_RUN + " (SUCCESS)"));
  }

  @Test
  void rejectsWrongInputType() {
    GetReconciliationStatusTool tool =
        new GetReconciliationStatusTool(
            mock(MetricQueryService.class),
            mock(SalesMetricsQuery.class),
            mock(ProductMetricsQuery.class),
            mock(ConnectorHealthQuery.class));

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetReconciliationStatusInput");
  }

  private static Map<String, Object> row(String domain, String status) {
    return Map.of("domain", domain, "status", status);
  }

  private static List<LocalDate> missing(int days) {
    return java.util.stream.IntStream.range(0, days)
        .mapToObj(i -> LocalDate.of(2026, 9, 1).plusDays(i))
        .toList();
  }

  private static TimeSeriesResult tsResult(MetricId id, List<LocalDate> missingPeriods) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved",
            Instant.EPOCH,
            missingPeriods,
            "1");
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of())), List.of(), provenance);
  }

  private record OtherInput() implements ToolInput {}
}
