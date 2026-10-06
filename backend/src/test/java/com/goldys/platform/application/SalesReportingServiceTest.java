package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SalesReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  private final PermissionService permissions = mock(PermissionService.class);
  private final SalesMetricsQuery salesMetrics = mock(SalesMetricsQuery.class);
  private final CanonicalDailySalesQuery dailySales = mock(CanonicalDailySalesQuery.class);
  private final SalesReportingService service =
      new SalesReportingService(salesMetrics, dailySales, permissions);

  @Test
  void latestTradingDayComesFromTheSemanticLayer() {
    when(salesMetrics.latestTradingDay())
        .thenReturn(
            Optional.of(new DailySalesMetric(SEP_13, new BigDecimal("10865.72"), "agreed", false)));

    var latest = service.latestTradingDay(OWNER);

    assertThat(latest.date()).isEqualTo("2026-09-13");
    assertThat(latest.total()).isEqualByComparingTo("10865.72");
    assertThat(latest.authoritativeSource()).isEqualTo("agreed");
  }

  @Test
  void latestTradingDayReturnsNullsWhenEmpty() {
    when(salesMetrics.latestTradingDay()).thenReturn(Optional.empty());

    var latest = service.latestTradingDay(OWNER);

    assertThat(latest.date()).isNull();
    assertThat(latest.total()).isNull();
  }

  @Test
  void dailySalesListsPerSourceProvenanceRows() {
    when(dailySales.currentDailySales())
        .thenReturn(
            List.of(
                new DailySalesView(
                    "CTB", SEP_13, new BigDecimal("100"), null, null, Instant.EPOCH)));

    var rows = service.dailySales(OWNER);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).source()).isEqualTo("CTB");
  }

  @Test
  void readDeniedThrows() {
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> service.latestTradingDay(OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }
}
