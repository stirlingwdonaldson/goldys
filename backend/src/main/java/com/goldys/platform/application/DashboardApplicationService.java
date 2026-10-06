package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Composes the dashboard read model from the semantic layer and operational metrics. The single
 * place a dashboard metric is computed, so REST, exports, and future consumers converge here.
 */
@Service
public class DashboardApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final int TOP_SELLERS_LIMIT = 5;
  private static final int TOP_SELLERS_WINDOW_DAYS = 30;
  private static final int TREND_DAYS = 13; // trailing 14 days inclusive
  private static final int ACTIVITY_DAYS = 14;

  private final SalesMetricsQuery salesMetrics;
  private final ProductMetricsQuery productMetrics;
  private final IngestionService ingestion;
  private final OverrideUsageService overrideUsage;
  private final PermissionService permissions;

  public DashboardApplicationService(
      SalesMetricsQuery salesMetrics,
      ProductMetricsQuery productMetrics,
      IngestionService ingestion,
      OverrideUsageService overrideUsage,
      PermissionService permissions) {
    this.salesMetrics = salesMetrics;
    this.productMetrics = productMetrics;
    this.ingestion = ingestion;
    this.overrideUsage = overrideUsage;
    this.permissions = permissions;
  }

  public Summary summary(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return summaryInternal();
  }

  public List<ActivityPoint> activity(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return activityInternal();
  }

  public List<TopSeller> topSellers(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return topSellersInternal();
  }

  public List<SalesTrend> salesTrend(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return salesTrendInternal();
  }

  /**
   * The dashboard's whole initial render in one authorization + one round trip, instead of five
   * separate requests. Drill-down endpoints stay separate.
   */
  public Bootstrap bootstrap(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return new Bootstrap(
        summaryInternal(),
        latestTradingDayInternal(),
        salesTrendInternal(),
        activityInternal(),
        topSellersInternal());
  }

  private Summary summaryInternal() {
    int openConflicts = (int) (salesMetrics.openConflicts() + productMetrics.openConflicts());
    IngestionHealth health = ingestion.health();
    OverrideUsage usage = overrideUsage.usage();
    return new Summary(
        health.completenessPercent(), openConflicts, health.timeToDetectFailure(), usage);
  }

  private List<ActivityPoint> activityInternal() {
    return ingestion.activity(ACTIVITY_DAYS).stream()
        .map(p -> new ActivityPoint(p.date(), p.clean(), p.failed()))
        .toList();
  }

  private List<TopSeller> topSellersInternal() {
    LocalDate to = LocalDate.now();
    return productMetrics
        .topSellers(to.minusDays(TOP_SELLERS_WINDOW_DAYS), to, TOP_SELLERS_LIMIT)
        .stream()
        .map(t -> new TopSeller(t.productName(), t.quantitySold(), t.amount(), t.hasConflict()))
        .toList();
  }

  private List<SalesTrend> salesTrendInternal() {
    LocalDate to = LocalDate.now();
    return salesMetrics.dailySales(to.minusDays(TREND_DAYS), to).stream()
        .map(m -> new SalesTrend(m.tradingDate().toString(), m.grossSales()))
        .toList();
  }

  private LatestSales latestTradingDayInternal() {
    return salesMetrics
        .latestTradingDay()
        .map(
            v ->
                new LatestSales(
                    v.tradingDate().toString(), v.grossSales(), v.authoritativeSource()))
        .orElseGet(() -> new LatestSales(null, null, null));
  }

  public record Bootstrap(
      Summary summary,
      LatestSales latestSales,
      List<SalesTrend> salesTrend,
      List<ActivityPoint> activity,
      List<TopSeller> topSellers) {}

  public record Summary(
      Integer ingestionCompleteness,
      Integer openConflicts,
      String timeToDetectFailure,
      OverrideUsage overrideUsage) {}

  public record TopSeller(
      String name, BigDecimal quantitySold, BigDecimal amount, boolean hasConflict) {}

  public record SalesTrend(String date, BigDecimal total) {}

  public record ActivityPoint(String date, int clean, int failed) {}

  public record LatestSales(String date, BigDecimal total, String authoritativeSource) {}
}
