package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.IngestionActivityPoint;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductRanking;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCoverage;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Composes the dashboard read model from the semantic layer and operational metrics. The single
 * place a dashboard metric is computed, so REST, exports, and future consumers converge here.
 */
@Service
public class DashboardApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");
  private static final int TOP_SELLERS_WINDOW_DAYS = 30;
  private static final int TOP_SELLERS_LIMIT = 5;
  private static final int TREND_DAYS = 14;
  private static final int ACTIVITY_DAYS = 14;

  private final SalesMetricsQuery salesMetrics;
  private final ProductMetricsQuery productMetrics;
  private final ReservationReportingService reservationReporting;
  private final InventoryReportingService inventoryReporting;
  private final IngestionService ingestion;
  private final OverrideUsageService overrideUsage;
  private final PermissionService permissions;
  private final MetricQueryService metrics;
  private final VenueClock venueClock;

  public DashboardApplicationService(
      SalesMetricsQuery salesMetrics,
      ProductMetricsQuery productMetrics,
      ReservationReportingService reservationReporting,
      InventoryReportingService inventoryReporting,
      IngestionService ingestion,
      OverrideUsageService overrideUsage,
      PermissionService permissions,
      MetricQueryService metrics,
      VenueClock venueClock) {
    this.salesMetrics = salesMetrics;
    this.productMetrics = productMetrics;
    this.reservationReporting = reservationReporting;
    this.inventoryReporting = inventoryReporting;
    this.ingestion = ingestion;
    this.overrideUsage = overrideUsage;
    this.permissions = permissions;
    this.metrics = metrics;
    this.venueClock = venueClock;
  }

  public Summary summary(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return summaryInternal();
  }

  public List<ActivityPoint> activity(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return activityInternal(from, to);
  }

  public TopSellersResult topSellers(UserRole role, LocalDate from, LocalDate to, Integer limit) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return topSellersInternal(from, to, limit);
  }

  public SalesTrendResult salesTrend(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return salesTrendInternal(from, to);
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
        salesTrendInternal(null, null),
        activityInternal(null, null),
        topSellersInternal(null, null, null),
        coversToday(role),
        foodCost(role));
  }

  private Summary summaryInternal() {
    int openConflicts = (int) (salesMetrics.openConflicts() + productMetrics.openConflicts());
    IngestionHealth health = ingestion.health();
    OverrideUsage usage = overrideUsage.usage();
    return new Summary(
        health.completenessPercent(), openConflicts, health.timeToDetectFailure(), usage);
  }

  private List<ActivityPoint> activityInternal(LocalDate from, LocalDate to) {
    List<IngestionActivityPoint> points;
    if (from == null && to == null) {
      points = ingestion.activity(ACTIVITY_DAYS);
    } else {
      LocalDate end = to != null ? to : venueClock.today();
      LocalDate start = from != null ? from : end.minusDays(ACTIVITY_DAYS - 1L);
      points = ingestion.activity(start, end);
    }
    return points.stream().map(p -> new ActivityPoint(p.date(), p.clean(), p.failed())).toList();
  }

  private TopSellersResult topSellersInternal(LocalDate from, LocalDate to, Integer limit) {
    // Read the resolved product metric directly rather than through the PRODUCT_TOP_SELLERS
    // catalogue metric: the catalogue path has no per-call limit (its executor hardcodes 5), while
    // this screen read needs a caller-controlled window and limit. Both retain the shared
    // qualified ranking, including coverage computed before the limit.
    TimeRange range = defaultRange(from, to, TOP_SELLERS_WINDOW_DAYS);
    int n = limit != null ? limit : TOP_SELLERS_LIMIT;
    ProductRanking ranking = productMetrics.qualifiedTopSellers(range.from(), range.to(), n);
    return new TopSellersResult(
        ranking.items().stream()
            .map(t -> new TopSeller(t.productName(), t.quantitySold(), t.amount(), t.hasConflict()))
            .toList(),
        ranking.notices(),
        ranking.coverage());
  }

  private SalesTrendResult salesTrendInternal(LocalDate from, LocalDate to) {
    TimeRange range = defaultRange(from, to, TREND_DAYS);
    TimeSeriesResult result =
        (TimeSeriesResult)
            metrics.query(
                new MetricQuery(MetricId.SALES_NET, range, TimeGrain.DAY, Set.of(), null));
    return new SalesTrendResult(
        result.series().stream()
            .flatMap(s -> s.points().stream())
            .map(p -> new SalesTrend(p.bucketStart().toString(), p.value()))
            .toList(),
        result.notices(),
        result.provenance());
  }

  /** An explicit range when supplied, otherwise the trailing {@code trailingDays} ending today. */
  private TimeRange defaultRange(LocalDate from, LocalDate to, int trailingDays) {
    LocalDate end = to != null ? to : venueClock.today();
    LocalDate start = from != null ? from : end.minusDays(trailingDays - 1L);
    return new TimeRange(start, end, Calendar.CALENDAR);
  }

  private LatestSales latestTradingDayInternal() {
    return salesMetrics
        .latestTradingDay()
        .map(
            v -> new LatestSales(v.tradingDate().toString(), v.netSales(), v.authoritativeSource()))
        .orElseGet(() -> new LatestSales(null, null, null));
  }

  /** Today's resolved reservation figures, or nulls when there is no data yet. */
  private CoversToday coversToday(UserRole role) {
    return reservationReporting
        .summary(role, venueClock.today())
        .map(s -> new CoversToday(s.covers(), s.bookings(), s.noShows()))
        .orElseGet(() -> new CoversToday(null, null, null));
  }

  /** Food-cost over the trailing window, as purchases ÷ resolved gross sales. */
  private FoodCost foodCost(UserRole role) {
    TimeRange range = defaultRange(null, null, TOP_SELLERS_WINDOW_DAYS);
    InventoryReportingService.InventorySummary s =
        inventoryReporting.summary(role, range.from(), range.to());
    return new FoodCost(s.foodCostPercent(), s.purchases(), s.wastage());
  }

  public record Bootstrap(
      Summary summary,
      LatestSales latestSales,
      SalesTrendResult salesTrend,
      List<ActivityPoint> activity,
      TopSellersResult topSellers,
      CoversToday coversToday,
      FoodCost foodCost) {}

  public record Summary(
      Integer ingestionCompleteness,
      Integer openConflicts,
      String timeToDetectFailure,
      OverrideUsage overrideUsage) {}

  public record TopSeller(
      String name, BigDecimal quantitySold, BigDecimal amount, boolean hasConflict) {}

  public record SalesTrend(String date, BigDecimal total) {}

  public record SalesTrendResult(
      List<SalesTrend> points, List<String> notices, MetricProvenance provenance) {}

  public record TopSellersResult(
      List<TopSeller> items, List<String> notices, MetricCoverage coverage) {}

  public record ActivityPoint(String date, int clean, int failed) {}

  public record LatestSales(String date, BigDecimal total, String authoritativeSource) {}

  public record CoversToday(Long covers, Long bookings, Long noShows) {}

  public record FoodCost(BigDecimal foodCostPercent, BigDecimal purchases, BigDecimal wastage) {}
}
