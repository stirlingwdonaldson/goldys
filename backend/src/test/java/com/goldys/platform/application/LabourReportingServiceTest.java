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
import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.Instant;
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
            mock(LabourMetricsQuery.class), mock(MetricQueryService.class), permissions);

    assertThatThrownBy(() -> service.summary(OWNER, FROM, TO))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summaryComputesCrossDomainMetrics() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
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

    MetricQueryService metrics =
        metricService(
            MetricId.LABOUR_SCHEDULED_HOURS, new BigDecimal("14.00"),
            MetricId.LABOUR_ACTUAL_HOURS, new BigDecimal("13.50"),
            MetricId.LABOUR_COST, new BigDecimal("350.00"),
            MetricId.LABOUR_HOURS_VARIANCE, new BigDecimal("0.50"),
            MetricId.RESERVATIONS_COVERS, new BigDecimal("310"),
            MetricId.SALES_GROSS, new BigDecimal("10000.00"));

    PermissionService permissions = mock(PermissionService.class);
    LabourReportingService service = new LabourReportingService(labour, metrics, permissions);

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

    MetricQueryService metrics =
        metricService(
            MetricId.LABOUR_SCHEDULED_HOURS, new BigDecimal("14.00"),
            MetricId.LABOUR_ACTUAL_HOURS, new BigDecimal("13.50"),
            MetricId.LABOUR_COST, null,
            MetricId.LABOUR_HOURS_VARIANCE, new BigDecimal("0.50"),
            MetricId.RESERVATIONS_COVERS, new BigDecimal("310"),
            MetricId.SALES_GROSS, new BigDecimal("10000.00"));

    LabourReportingService service =
        new LabourReportingService(labour, metrics, mock(PermissionService.class));

    LabourReportingService.LabourSummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.labourCost()).isNull();
    assertThat(summary.labourCostPerCover()).isNull();
    assertThat(summary.fohLabourCostPercent()).isNull();
  }

  @Test
  void zeroCoversYieldsNullPerCoverMetrics() {
    LabourMetricsQuery labour = mock(LabourMetricsQuery.class);
    when(labour.dailyLabour(FROM, TO)).thenReturn(List.of());

    MetricQueryService metrics =
        metricService(
            MetricId.LABOUR_SCHEDULED_HOURS, new BigDecimal("14.00"),
            MetricId.LABOUR_ACTUAL_HOURS, new BigDecimal("13.50"),
            MetricId.LABOUR_COST, new BigDecimal("350.00"),
            MetricId.LABOUR_HOURS_VARIANCE, new BigDecimal("0.50"),
            MetricId.RESERVATIONS_COVERS, BigDecimal.ZERO,
            MetricId.SALES_GROSS, new BigDecimal("10000.00"));

    LabourReportingService service =
        new LabourReportingService(labour, metrics, mock(PermissionService.class));

    LabourReportingService.LabourSummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.hoursPerCover()).isNull();
    assertThat(summary.labourCostPerCover()).isNull();
  }

  /** Builds a {@link MetricQueryService} mock that resolves each (metric, value) pair. */
  private static MetricQueryService metricService(Object... idValues) {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricQuery query = inv.getArgument(0);
              for (int i = 0; i < idValues.length; i += 2) {
                if (idValues[i] == query.metric()) {
                  return series(query, (BigDecimal) idValues[i + 1]);
                }
              }
              throw new AssertionError("unexpected metric " + query.metric());
            });
    return metrics;
  }

  private static TimeSeriesResult series(MetricQuery query, BigDecimal value) {
    MetricProvenance provenance =
        new MetricProvenance(
            query.metric(),
            "1",
            query.range(),
            TimeGrain.DAY,
            "resolved_labour_day",
            Instant.EPOCH,
            List.of(),
            "1");
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, List.of(new MetricPoint(query.range().from(), value)))),
        List.of(),
        provenance);
  }
}
