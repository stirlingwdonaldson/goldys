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

class InventoryReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);

  @Test
  void summaryRequiresReadPermission() {
    PermissionService permissions = mock(PermissionService.class);
    org.mockito.Mockito.doThrow(AccessDeniedException.forResource("inventory.cost"))
        .when(permissions)
        .require(any(), any(), any());

    InventoryReportingService service =
        new InventoryReportingService(mock(MetricQueryService.class), permissions);

    assertThatThrownBy(() -> service.summary(OWNER, FROM, TO))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summaryComputesFoodCostPercent() {
    MetricQueryService metrics =
        metricService(
            MetricId.INVENTORY_PURCHASES, new BigDecimal("70.00"),
            MetricId.INVENTORY_WASTAGE, null,
            MetricId.SALES_GROSS, new BigDecimal("1000.00"));

    PermissionService permissions = mock(PermissionService.class);
    InventoryReportingService service = new InventoryReportingService(metrics, permissions);

    InventoryReportingService.InventorySummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.purchases()).isEqualByComparingTo(new BigDecimal("70.00"));
    assertThat(summary.foodCostPercent()).isEqualByComparingTo(new BigDecimal("0.0700"));
    verify(permissions).require(OWNER, new ResourceKey("inventory.cost"), PermissionAction.READ);
  }

  @Test
  void zeroSalesYieldsNullFoodCostPercent() {
    MetricQueryService metrics =
        metricService(
            MetricId.INVENTORY_PURCHASES, new BigDecimal("70.00"),
            MetricId.INVENTORY_WASTAGE, null,
            MetricId.SALES_GROSS, null);

    InventoryReportingService service =
        new InventoryReportingService(metrics, mock(PermissionService.class));

    InventoryReportingService.InventorySummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.foodCostPercent()).isNull();
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
            "resolved_inventory",
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
