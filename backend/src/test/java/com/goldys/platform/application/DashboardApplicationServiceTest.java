package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.VenueClock;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductRanking;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCoverage;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DashboardApplicationServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  private final PermissionService permissions = mock(PermissionService.class);
  private final SalesMetricsQuery salesMetrics = mock(SalesMetricsQuery.class);
  private final ProductMetricsQuery productMetrics = mock(ProductMetricsQuery.class);
  private final ReservationReportingService reservationReporting =
      mock(ReservationReportingService.class);
  private final InventoryReportingService inventoryReporting =
      mock(InventoryReportingService.class);
  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final IngestionService ingestion = mock(IngestionService.class);
  private final OverrideUsageService overrideUsage = mock(OverrideUsageService.class);

  private final DashboardApplicationService service =
      new DashboardApplicationService(
          salesMetrics,
          productMetrics,
          reservationReporting,
          inventoryReporting,
          ingestion,
          overrideUsage,
          permissions,
          metrics,
          VenueClock.fixed(
              java.time.ZoneId.of("Australia/Melbourne"), Instant.parse("2026-09-13T00:00:00Z")));

  @Test
  void summaryAuthorizesThenComposes() {
    when(salesMetrics.openConflicts()).thenReturn(2L);
    when(productMetrics.openConflicts()).thenReturn(3L);
    when(ingestion.health()).thenReturn(new IngestionHealth(92, "42m avg"));
    when(overrideUsage.usage()).thenReturn(new OverrideUsage(5, "last 7 days"));

    var summary = service.summary(OWNER);

    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    assertThat(summary.ingestionCompleteness()).isEqualTo(92);
    assertThat(summary.openConflicts()).isEqualTo(5); // daily + product
    assertThat(summary.timeToDetectFailure()).isEqualTo("42m avg");
    assertThat(summary.overrideUsage().count()).isEqualTo(5);
  }

  @Test
  void readDeniedThrows() {
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service.summary(OWNER)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void topSellersMapsSemanticToResponse() {
    when(productMetrics.qualifiedTopSellers(any(), any(), eq(5)))
        .thenReturn(
            new ProductRanking(
                List.of(new TopSeller("chips", new BigDecimal("10"), new BigDecimal("50"), true)),
                new MetricCoverage(
                    SEP_13.minusDays(29), SEP_13, List.of(), List.of(SEP_13), List.of()),
                List.of("Incomplete subtotal")));

    var sellers = service.topSellers(OWNER, null, null, null);

    assertThat(sellers.items()).hasSize(1);
    assertThat(sellers.notices()).containsExactly("Incomplete subtotal");
    verify(productMetrics).qualifiedTopSellers(SEP_13.minusDays(29), SEP_13, 5);
    assertThat(sellers.items().get(0).name()).isEqualTo("chips");
    assertThat(sellers.items().get(0).quantitySold()).isEqualByComparingTo("10");
    assertThat(sellers.items().get(0).amount()).isEqualByComparingTo("50");
    assertThat(sellers.items().get(0).hasConflict()).isTrue();
  }

  @Test
  void salesTrendMapsMetricToResponse() {
    when(metrics.query(any()))
        .thenReturn(
            new TimeSeriesResult(
                MetricId.SALES_NET,
                List.of(
                    new MetricSeries(
                        null, List.of(new MetricPoint(SEP_13, new BigDecimal("100"))))),
                List.of("Incomplete subtotal"),
                provenance(MetricId.SALES_NET)));

    var trend = service.salesTrend(OWNER, null, null);

    assertThat(trend.points()).hasSize(1);
    assertThat(trend.notices()).containsExactly("Incomplete subtotal");
    assertThat(trend.provenance()).isEqualTo(provenance(MetricId.SALES_NET));
    verify(metrics)
        .query(
            new com.goldys.platform.semantic.catalog.MetricQuery(
                MetricId.SALES_NET,
                new TimeRange(SEP_13.minusDays(13), SEP_13, Calendar.CALENDAR),
                TimeGrain.DAY,
                java.util.Set.of(),
                null));
    assertThat(trend.points().get(0).date()).isEqualTo("2026-09-13");
    assertThat(trend.points().get(0).total()).isEqualByComparingTo("100");
  }

  @Test
  void deniedDashboardReadsDoNotReachSemanticQueries() {
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());
    assertThatThrownBy(() -> service.salesTrend(OWNER, null, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.topSellers(OWNER, null, null, null))
        .isInstanceOf(AccessDeniedException.class);
    org.mockito.Mockito.verifyNoInteractions(metrics, productMetrics);
  }

  @Test
  void bootstrapComposesCoversTodayAndFoodCost() {
    when(salesMetrics.openConflicts()).thenReturn(0L);
    when(productMetrics.openConflicts()).thenReturn(0L);
    when(ingestion.health()).thenReturn(new IngestionHealth(92, "42m avg"));
    when(overrideUsage.usage()).thenReturn(new OverrideUsage(0, "never"));
    when(salesMetrics.latestTradingDay()).thenReturn(Optional.empty());
    when(metrics.query(any()))
        .thenReturn(
            new TimeSeriesResult(
                MetricId.SALES_GROSS, List.of(), List.of(), provenance(MetricId.SALES_GROSS)));
    when(ingestion.activity(anyInt())).thenReturn(List.of());
    when(productMetrics.qualifiedTopSellers(any(), any(), eq(5)))
        .thenReturn(
            new ProductRanking(
                List.of(),
                new MetricCoverage(SEP_13.minusDays(29), SEP_13, List.of(), List.of(), List.of()),
                List.of()));
    when(reservationReporting.summary(eq(OWNER), any()))
        .thenReturn(
            Optional.of(new ReservationSummary(SEP_13, 12, 10, 34, 1, 2, 4, 0L, null, null, null)));
    when(inventoryReporting.summary(eq(OWNER), any(), any()))
        .thenReturn(
            new InventoryReportingService.InventorySummary(
                "2026-08-14",
                "2026-09-13",
                new BigDecimal("5000"),
                new BigDecimal("120"),
                new BigDecimal("0.3125"),
                List.of()));

    var bootstrap = service.bootstrap(OWNER);

    assertThat(bootstrap.coversToday().covers()).isEqualTo(34L);
    assertThat(bootstrap.coversToday().bookings()).isEqualTo(12L);
    assertThat(bootstrap.coversToday().noShows()).isEqualTo(2L);
    assertThat(bootstrap.foodCost().foodCostPercent()).isEqualByComparingTo("0.3125");
    assertThat(bootstrap.foodCost().purchases()).isEqualByComparingTo("5000");
    assertThat(bootstrap.foodCost().wastage()).isEqualByComparingTo("120");
  }

  private static MetricProvenance provenance(MetricId id) {
    TimeRange range = new TimeRange(SEP_13.minusDays(30), SEP_13, Calendar.CALENDAR);
    return new MetricProvenance(
        id, "1", range, TimeGrain.DAY, "resolved", Instant.EPOCH, List.of(), "1");
  }
}
