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
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.semantic.TopSeller;
import java.math.BigDecimal;
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
  private final IngestionService ingestion = mock(IngestionService.class);
  private final OverrideUsageService overrideUsage = mock(OverrideUsageService.class);

  private final DashboardApplicationService service =
      new DashboardApplicationService(
          salesMetrics, productMetrics, ingestion, overrideUsage, permissions);

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
    when(productMetrics.topSellers(any(), any(), any(int.class)))
        .thenReturn(
            List.of(new TopSeller("chips", new BigDecimal("10"), new BigDecimal("50"), false)));

    var sellers = service.topSellers(OWNER);

    assertThat(sellers).hasSize(1);
    assertThat(sellers.get(0).name()).isEqualTo("chips");
    assertThat(sellers.get(0).amount()).isEqualByComparingTo("50");
    assertThat(sellers.get(0).hasConflict()).isFalse();
  }

  @Test
  void salesTrendMapsMetricToResponse() {
    when(salesMetrics.dailySales(any(), any()))
        .thenReturn(
            List.of(
                new DailySalesMetric(SEP_13, new BigDecimal("100"), null, null, "agreed", false)));

    var trend = service.salesTrend(OWNER);

    assertThat(trend).hasSize(1);
    assertThat(trend.get(0).date()).isEqualTo("2026-09-13");
    assertThat(trend.get(0).total()).isEqualByComparingTo("100");
  }
}
