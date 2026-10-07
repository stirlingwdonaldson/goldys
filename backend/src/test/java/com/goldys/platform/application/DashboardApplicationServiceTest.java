package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricRankedItem;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardApplicationServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  private final PermissionService permissions = mock(PermissionService.class);
  private final SalesMetricsQuery salesMetrics = mock(SalesMetricsQuery.class);
  private final ProductMetricsQuery productMetrics = mock(ProductMetricsQuery.class);
  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final IngestionService ingestion = mock(IngestionService.class);
  private final OverrideUsageService overrideUsage = mock(OverrideUsageService.class);

  private final DashboardApplicationService service =
      new DashboardApplicationService(
          salesMetrics, productMetrics, ingestion, overrideUsage, permissions, metrics);

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
    when(metrics.query(any()))
        .thenReturn(
            new RankedListResult(
                MetricId.PRODUCT_TOP_SELLERS,
                List.of(new MetricRankedItem("chips", new BigDecimal("50"), new BigDecimal("10"))),
                List.of(),
                provenance(MetricId.PRODUCT_TOP_SELLERS)));

    var sellers = service.topSellers(OWNER);

    assertThat(sellers).hasSize(1);
    assertThat(sellers.get(0).name()).isEqualTo("chips");
    assertThat(sellers.get(0).quantitySold()).isEqualByComparingTo("10");
    assertThat(sellers.get(0).amount()).isEqualByComparingTo("50");
    assertThat(sellers.get(0).hasConflict()).isFalse();
  }

  @Test
  void salesTrendMapsMetricToResponse() {
    when(metrics.query(any()))
        .thenReturn(
            new TimeSeriesResult(
                MetricId.SALES_GROSS,
                List.of(
                    new MetricSeries(
                        null, List.of(new MetricPoint(SEP_13, new BigDecimal("100"))))),
                List.of(),
                provenance(MetricId.SALES_GROSS)));

    var trend = service.salesTrend(OWNER);

    assertThat(trend).hasSize(1);
    assertThat(trend.get(0).date()).isEqualTo("2026-09-13");
    assertThat(trend.get(0).total()).isEqualByComparingTo("100");
  }

  private static MetricProvenance provenance(MetricId id) {
    TimeRange range = new TimeRange(SEP_13.minusDays(30), SEP_13, Calendar.CALENDAR);
    return new MetricProvenance(
        id, "1", range, TimeGrain.DAY, "resolved", Instant.EPOCH, List.of(), "1");
  }
}
