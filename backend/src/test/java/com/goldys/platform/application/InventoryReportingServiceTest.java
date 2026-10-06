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
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
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
        new InventoryReportingService(
            mock(InventoryMetricsQuery.class), mock(SalesMetricsQuery.class), permissions);

    assertThatThrownBy(() -> service.summary(OWNER, FROM, TO))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summaryComputesFoodCostPercent() {
    InventoryMetricsQuery inventory = mock(InventoryMetricsQuery.class);
    when(inventory.purchases(FROM, TO)).thenReturn(new BigDecimal("70.00"));
    when(inventory.wastage(FROM, TO)).thenReturn(null);

    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.dailySales(FROM, TO))
        .thenReturn(List.of(new DailySalesMetric(FROM, new BigDecimal("1000.00"), "agreed", false)));

    PermissionService permissions = mock(PermissionService.class);
    InventoryReportingService service =
        new InventoryReportingService(inventory, sales, permissions);

    InventoryReportingService.InventorySummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.purchases()).isEqualByComparingTo(new BigDecimal("70.00"));
    assertThat(summary.foodCostPercent()).isEqualByComparingTo(new BigDecimal("0.0700"));
    verify(permissions).require(OWNER, new ResourceKey("inventory.cost"), PermissionAction.READ);
  }

  @Test
  void zeroSalesYieldsNullFoodCostPercent() {
    InventoryMetricsQuery inventory = mock(InventoryMetricsQuery.class);
    when(inventory.purchases(FROM, TO)).thenReturn(new BigDecimal("70.00"));
    when(inventory.wastage(FROM, TO)).thenReturn(null);

    SalesMetricsQuery sales = mock(SalesMetricsQuery.class);
    when(sales.dailySales(FROM, TO)).thenReturn(List.of());

    InventoryReportingService service =
        new InventoryReportingService(inventory, sales, mock(PermissionService.class));

    InventoryReportingService.InventorySummary summary = service.summary(OWNER, FROM, TO);

    assertThat(summary.foodCostPercent()).isNull();
  }
}
