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
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The Inventory screen's read model. Authorizes the read and composes inventory COGS with resolved
 * sales to produce the food-cost percentage.
 */
@Service
public class InventoryReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("inventory.cost");
  private static final int SCALE = 4;

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

  /**
   * Period-total ratios are computed here as ratio-of-sums over catalogue base metrics; the derived
   * {@code *_PERCENT}/{@code *_PER_COVER} metrics are the time-series form and will replace this
   * once a TOTAL grain exists.
   */
  public InventorySummary summary(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);

    TimeRange range = new TimeRange(from, to, Calendar.CALENDAR);
    BigDecimal purchases = total(MetricId.INVENTORY_PURCHASES, range);
    BigDecimal wastage = total(MetricId.INVENTORY_WASTAGE, range);
    BigDecimal grossSales = total(MetricId.SALES_GROSS, range);
    BigDecimal foodCostPercent =
        purchases == null || grossSales == null || grossSales.signum() == 0
            ? null
            : purchases.divide(grossSales, SCALE, RoundingMode.HALF_UP);

    return new InventorySummary(
        from.toString(), to.toString(), purchases, wastage, foodCostPercent);
  }

  /** Sums non-null per-day points across all series; null when nothing resolved (never zero). */
  private BigDecimal total(MetricId id, TimeRange range) {
    TimeSeriesResult result =
        (TimeSeriesResult) metrics.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries series : result.series()) {
      for (MetricPoint point : series.points()) {
        if (point.value() != null) {
          total = total.add(point.value());
          any = true;
        }
      }
    }
    return any ? total : null;
  }

  public record InventorySummary(
      String from,
      String to,
      BigDecimal purchases,
      BigDecimal wastage,
      BigDecimal foodCostPercent) {}

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
