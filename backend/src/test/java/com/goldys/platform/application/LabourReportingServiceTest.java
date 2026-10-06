package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabourReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);

  @Test
  void summaryRequiresHoursAndCostPermissions() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("labour.hours"))
        .when(permissions)
        .require(any(), any(), any());

    LabourReportingService service =
        new LabourReportingService(
            mock(LabourMetricsQuery.class),
            mock(ReservationMetricsQuery.class),
            mock(SalesMetricsQuery.class),
            permissions);

    assertThatThrownBy(() -> service.summary(OWNER, FROM, TO))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summaryComputesCrossDomainMetrics() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.scheduledHours(FROM, TO)).thenReturn(new BigDecimal("14.00"));
    when(labour.actualHours(FROM, TO)).thenReturn(new BigDecimal("13.50"));
    when(labour.labourCost(FROM, TO)).thenReturn(new BigDecimal("350.00"));
    when(labour.dailyLabour(FROM, TO))
        .thenReturn(
            List.of(
                new LabourMetric(
                    FROM,
                    "FOH",
                    new BigDecimal("8"),
                    new BigDecimal("7.5"),
                    null,
                    new BigDecimal("200.00"),
                    "DEPUTY",
                    false),
                new LabourMetric(
                    FROM,
                    "BOH",
                    new BigDecimal("6"),
                    new BigDecimal("6"),
                    null,
                    new BigDecimal("150.00"),
                    "DEPUTY",
                    false)));

    ReservationMetricsQuery reservations = mock(ReservationMetricsQuery.class);
    when(reservations.dailyCovers(FROM, TO))
        .thenReturn(List.of(new CoversMetric(FROM, 310, "OPENTABLE", false)));

    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.dailySales(FROM, TO))
        .thenReturn(
            List.of(new DailySalesMetric(FROM, new BigDecimal("10000.00"), "agreed", false)));

    PermissionService permissions = mock(PermissionService.class);
    LabourReportingService service =
        new LabourReportingService(labour, reservations, sales, permissions);

    LabourReportingService.LabourSummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.actualHours()).isEqualByComparingTo(new BigDecimal("13.50"));
    assertThat(summary.labourCost()).isEqualByComparingTo(new BigDecimal("350.00"));
    assertThat(summary.hoursPerCover()).isEqualByComparingTo(new BigDecimal("0.0435"));
    assertThat(summary.labourCostPerCover()).isEqualByComparingTo(new BigDecimal("1.1290"));
    assertThat(summary.fohLabourCostPercent()).isEqualByComparingTo(new BigDecimal("0.0200"));
    assertThat(summary.bohLabourCostPercent()).isEqualByComparingTo(new BigDecimal("0.0150"));

    verify(permissions).require(OWNER, new ResourceKey("labour.hours"), PermissionAction.READ);
    verify(permissions).require(OWNER, new ResourceKey("labour.cost"), PermissionAction.READ);
  }

  @Test
  void missingCostYieldsNullLabourCostMetrics() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.scheduledHours(FROM, TO)).thenReturn(new BigDecimal("14.00"));
    when(labour.actualHours(FROM, TO)).thenReturn(new BigDecimal("13.50"));
    when(labour.labourCost(FROM, TO)).thenReturn(null);
    when(labour.dailyLabour(FROM, TO))
        .thenReturn(
            List.of(
                new LabourMetric(
                    FROM,
                    "FOH",
                    new BigDecimal("8"),
                    new BigDecimal("7.5"),
                    null,
                    null,
                    "DEPUTY",
                    false)));

    ReservationMetricsQuery reservations = mock(ReservationMetricsQuery.class);
    when(reservations.dailyCovers(FROM, TO))
        .thenReturn(List.of(new CoversMetric(FROM, 310, "OPENTABLE", false)));
    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.dailySales(FROM, TO))
        .thenReturn(
            List.of(new DailySalesMetric(FROM, new BigDecimal("10000.00"), "agreed", false)));

    LabourReportingService service =
        new LabourReportingService(labour, reservations, sales, mock(PermissionService.class));

    LabourReportingService.LabourSummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.labourCost()).isNull();
    assertThat(summary.labourCostPerCover()).isNull();
    assertThat(summary.fohLabourCostPercent()).isNull();
  }

  @Test
  void zeroCoversYieldsNullPerCoverMetrics() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.scheduledHours(FROM, TO)).thenReturn(new BigDecimal("14.00"));
    when(labour.actualHours(FROM, TO)).thenReturn(new BigDecimal("13.50"));
    when(labour.labourCost(FROM, TO)).thenReturn(new BigDecimal("350.00"));
    when(labour.dailyLabour(FROM, TO)).thenReturn(List.of());

    ReservationMetricsQuery reservations = mock(ReservationMetricsQuery.class);
    when(reservations.dailyCovers(FROM, TO)).thenReturn(List.of());
    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.dailySales(FROM, TO))
        .thenReturn(
            List.of(new DailySalesMetric(FROM, new BigDecimal("10000.00"), "agreed", false)));

    LabourReportingService service =
        new LabourReportingService(labour, reservations, sales, mock(PermissionService.class));

    LabourReportingService.LabourSummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.hoursPerCover()).isNull();
    assertThat(summary.labourCostPerCover()).isNull();
  }
}
