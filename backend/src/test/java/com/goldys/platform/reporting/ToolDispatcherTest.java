package com.goldys.platform.reporting;

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
import com.goldys.platform.metrics.OperationalMetrics;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.StatWidgetSpec;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolDispatcherTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static ToolResult okResult() {
    return new ToolResult(
        new StatWidgetSpec("id", "Sales", null, null, null, null, null),
        List.of(),
        List.of(),
        List.of());
  }

  private static ReportingTool tool(ToolId id) {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(id);
    when(t.resource()).thenReturn(new ResourceKey("reconciliation.sales"));
    return t;
  }

  @Test
  void authorizesUsingTheToolsDeclaredResource() {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(ToolId.GET_LABOUR_COST);
    when(t.resource()).thenReturn(new ResourceKey("labour.cost"));
    when(t.execute(any(), any())).thenReturn(okResult());
    PermissionService permissions = mock(PermissionService.class);
    ToolDispatcher dispatcher =
        new ToolDispatcher(
            new ToolRegistry(List.of(t)),
            permissions,
            new MetricCatalog(),
            mock(OperationalMetrics.class));

    dispatcher.dispatch(ToolId.GET_LABOUR_COST, mock(ToolInput.class), OWNER);

    verify(permissions).require(OWNER, new ResourceKey("labour.cost"), PermissionAction.READ);
  }

  @Test
  void dispatchesToTheRegisteredTool() {
    ReportingTool t = tool(ToolId.GET_SALES_BY_PERIOD);
    when(t.execute(any(), any())).thenReturn(okResult());
    PermissionService permissions = mock(PermissionService.class);
    ToolDispatcher dispatcher =
        new ToolDispatcher(
            new ToolRegistry(List.of(t)),
            permissions,
            new MetricCatalog(),
            mock(OperationalMetrics.class));

    ToolResult result =
        dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER);

    assertThat(result.widget()).isInstanceOf(StatWidgetSpec.class);
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
  }

  @Test
  void rejectsAnUnknownTool() {
    ToolDispatcher dispatcher =
        new ToolDispatcher(
            new ToolRegistry(List.of()),
            mock(PermissionService.class),
            new MetricCatalog(),
            mock(OperationalMetrics.class));

    assertThatThrownBy(
            () -> dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown tool");
  }

  @Test
  void deniesAnUnauthorizedRole() {
    ReportingTool t = tool(ToolId.GET_SALES_BY_PERIOD);
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());
    ToolDispatcher dispatcher =
        new ToolDispatcher(
            new ToolRegistry(List.of(t)),
            permissions,
            new MetricCatalog(),
            mock(OperationalMetrics.class));

    assertThatThrownBy(
            () -> dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void authorizesEachMetricBeforeExecution() {
    ReportingTool t = mock(ReportingTool.class);
    when(t.id()).thenReturn(ToolId.GET_SALES_BY_PERIOD);
    when(t.resource())
        .thenReturn(new ResourceKey("conversational.chat")); // capability (general tool)
    when(t.toMetricQueries(any()))
        .thenReturn(
            List.of(
                new MetricQuery(
                    MetricId.SALES_GROSS,
                    new TimeRange(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), Calendar.CALENDAR),
                    TimeGrain.DAY,
                    Set.of(),
                    null)));
    when(t.execute(any(), any())).thenReturn(okResult());
    PermissionService permissions = mock(PermissionService.class);
    ToolDispatcher dispatcher =
        new ToolDispatcher(
            new ToolRegistry(List.of(t)),
            permissions,
            new MetricCatalog(),
            mock(OperationalMetrics.class));

    dispatcher.dispatch(ToolId.GET_SALES_BY_PERIOD, mock(ToolInput.class), OWNER);

    // capability gate ...
    verify(permissions)
        .require(OWNER, new ResourceKey("conversational.chat"), PermissionAction.READ);
    // ... and the per-metric gate (sales.gross -> reconciliation.sales), both before execution.
    verify(permissions)
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
  }
}
