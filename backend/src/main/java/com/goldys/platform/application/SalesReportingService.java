package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Sales screen's read model: a per-source reconciliation listing plus the resolved latest
 * total. The per-source list is provenance (not a business metric); the business metric is the
 * resolved total, sourced from {@link SalesMetricsQuery}.
 */
@Service
public class SalesReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final SalesMetricsQuery salesMetrics;
  private final CanonicalDailySalesQuery dailySales;
  private final PermissionService permissions;

  public SalesReportingService(
      SalesMetricsQuery salesMetrics,
      CanonicalDailySalesQuery dailySales,
      PermissionService permissions) {
    this.salesMetrics = salesMetrics;
    this.dailySales = dailySales;
    this.permissions = permissions;
  }

  /** Per-source daily-sales rows (newest first), for the Sales screen's reconciliation view. */
  public List<DailySalesRow> dailySales(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return dailySales.currentDailySales().stream()
        .sorted(Comparator.comparing(DailySalesView::tradingDate).reversed())
        .map(SalesReportingService::toRow)
        .toList();
  }

  /**
   * The latest trading date's resolved total, or nulls when there is no data or it is unresolved.
   */
  public LatestSales latestTradingDay(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return salesMetrics
        .latestTradingDay()
        .map(
            v ->
                new LatestSales(
                    v.tradingDate().toString(), v.grossSales(), v.authoritativeSource()))
        .orElseGet(() -> new LatestSales(null, null, null));
  }

  private static DailySalesRow toRow(DailySalesView v) {
    return new DailySalesRow(
        v.tradingDate().toString(), v.sourceSystem(), v.totalSales(), v.gstTotal(), v.netTotal());
  }

  public record DailySalesRow(
      String date, String source, BigDecimal totalSales, BigDecimal gst, BigDecimal net) {}

  public record LatestSales(String date, BigDecimal total, String authoritativeSource) {}
}
