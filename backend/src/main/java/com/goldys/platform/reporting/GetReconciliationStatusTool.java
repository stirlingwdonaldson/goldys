package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.ConnectorHealth;
import com.goldys.platform.semantic.ConnectorHealthQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reports reconciliation status — open-conflict counts, unresolved periods per domain, and
 * per-source connector freshness — as a table. The result is <em>about the data</em>, never metric
 * values, so {@link #toMetricQueries(ToolInput)} is empty and no per-metric gate applies.
 */
@Component
public class GetReconciliationStatusTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final SalesMetricsQuery sales;
  private final ProductMetricsQuery product;
  private final ConnectorHealthQuery connectors;

  public GetReconciliationStatusTool(
      MetricQueryService metrics,
      SalesMetricsQuery sales,
      ProductMetricsQuery product,
      ConnectorHealthQuery connectors) {
    this.metrics = metrics;
    this.sales = sales;
    this.product = product;
    this.connectors = connectors;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_RECONCILIATION_STATUS;
  }

  @Override
  public String name() {
    return "get_reconciliation_status";
  }

  @Override
  public String description() {
    return "Reports reconciliation status: open conflicts, unresolved periods per domain, and "
        + "per-source connector freshness.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetReconciliationStatusInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("reconciliation.status");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetReconciliationStatusInput)) {
      throw new IllegalArgumentException(
          "Expected GetReconciliationStatusInput, got " + input.getClass().getSimpleName());
    }
    List<Map<String, Object>> rows = new ArrayList<>();

    // Open-conflict counts, per domain that exposes them.
    rows.add(statusRow("Sales", sales.openConflicts() + " open conflicts"));
    rows.add(statusRow("Product", product.openConflicts() + " open conflicts"));

    // Unresolved periods, per domain, counted from each representative metric's missingPeriods.
    LocalDate today = LocalDate.now();
    TimeRange range = new TimeRange(today.minusDays(28), today, Calendar.CALENDAR);
    rows.add(statusRow("Sales", unresolvedDays(MetricId.SALES_GROSS, range) + " unresolved days"));
    rows.add(
        statusRow(
            "Reservations",
            unresolvedDays(MetricId.RESERVATIONS_COVERS, range) + " unresolved days"));
    rows.add(statusRow("Labour", unresolvedDays(MetricId.LABOUR_COST, range) + " unresolved days"));
    rows.add(
        statusRow(
            "Inventory", unresolvedDays(MetricId.INVENTORY_PURCHASES, range) + " unresolved days"));
    rows.add(
        statusRow(
            "Product", unresolvedDays(MetricId.PRODUCT_SALES_AMOUNT, range) + " unresolved days"));

    // Per-source connector freshness.
    for (ConnectorHealth c : connectors.health()) {
      rows.add(
          statusRow(
              c.source(), c.connector() + ": last run " + c.lastRunAt() + " (" + c.status() + ")"));
    }

    List<Column> columns =
        List.of(new Column("domain", "Domain", null), new Column("status", "Status", null));
    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Reconciliation status", null, columns, rows, null);
    return new ToolResult(widget, List.of(), List.of(), List.of());
  }

  private long unresolvedDays(MetricId id, TimeRange range) {
    MetricResult result = metrics.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    return result.provenance().missingPeriods().size();
  }

  private static Map<String, Object> statusRow(String domain, String status) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("domain", domain);
    row.put("status", status);
    return row;
  }
}
