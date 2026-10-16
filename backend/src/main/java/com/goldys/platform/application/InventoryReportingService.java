package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InvoiceLineMetricsQuery;
import com.goldys.platform.semantic.SupplierCogs;
import com.goldys.platform.semantic.UomUnitCost;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The inventory screen's authorised invoice-purchasing read model, with explicit coverage notes.
 */
@Service
public class InventoryReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");

  private final MetricQueryService metrics;
  private final InvoiceLineMetricsQuery lineMetrics;
  private final PermissionService permissions;

  public InventoryReportingService(
      MetricQueryService metrics,
      InvoiceLineMetricsQuery lineMetrics,
      PermissionService permissions) {
    this.metrics = metrics;
    this.lineMetrics = lineMetrics;
    this.permissions = permissions;
  }

  /** Known purchasing subtotals; consumed cost remains unavailable without valuation evidence. */
  public InventorySummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);

    PeriodMetricReader reader =
        new PeriodMetricReader(metrics, new TimeRange(from, to, Calendar.CALENDAR));
    BigDecimal purchases = reader.value(MetricId.INVENTORY_PURCHASES);
    BigDecimal wastage = reader.value(MetricId.INVENTORY_WASTAGE);
    List<String> notices = new java.util.ArrayList<>(reader.notices());
    notices.add(
        "Food cost % unavailable: invoice purchases are not consumed cost; valuation evidence is required");
    return new InventorySummary(
        from.toString(), to.toString(), purchases, wastage, null, List.copyOf(notices));
  }

  public record InventorySummary(
      String from,
      String to,
      BigDecimal purchases,
      BigDecimal wastage,
      BigDecimal foodCostPercent,
      List<String> notices) {}

  /** Line-level enrichment breakdown (unit cost per UOM, COGS/WET by supplier) for the range. */
  public LineBreakdown lineBreakdown(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return new LineBreakdown(
        from.toString(),
        to.toString(),
        lineMetrics.unitCostByUom(from, to),
        lineMetrics.cogsBySupplier(from, to));
  }

  public record LineBreakdown(
      String from, String to, List<UomUnitCost> unitCostByUom, List<SupplierCogs> cogsBySupplier) {}
}
