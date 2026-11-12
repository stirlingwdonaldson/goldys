package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.metrics.OperationalMetrics;
import com.goldys.platform.semantic.catalog.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreparedMetricToolTest {
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate DATE = LocalDate.of(2026, 1, 8);
  private final MetricCatalog catalog = new MetricCatalog();
  private final MetricQueryService metrics = mock(MetricQueryService.class);
  private final PermissionService permissions = mock(PermissionService.class);

  private ToolDispatcher dispatcher(ReportingTool tool) {
    return new ToolDispatcher(
        new ToolRegistry(List.of(tool)), permissions, catalog, mock(OperationalMetrics.class));
  }

  @Test
  void executesExactlyTheQueriesPreparedBeforeAuthorization() {
    GetSalesByPeriodTool tool = spy(new GetSalesByPeriodTool(metrics, new WidgetRenderer(catalog)));
    var input = new GetSalesByPeriodInput(DATE, DATE, MetricId.SALES_GROSS);
    MetricQuery planned =
        new MetricQuery(
            MetricId.SALES_GROSS,
            new TimeRange(DATE, DATE, Calendar.CALENDAR),
            TimeGrain.DAY,
            Set.of(),
            null);
    // A second planning call would fail: execution must consume the authorized plan.
    doReturn(List.of(planned))
        .doThrow(new AssertionError("planned twice"))
        .when(tool)
        .toMetricQueries(input);
    when(metrics.query(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));

    ToolResult output = dispatcher(tool).dispatch(tool.id(), input, OWNER);

    verify(tool, times(1)).toMetricQueries(input);
    ArgumentCaptor<MetricQuery> actual = ArgumentCaptor.forClass(MetricQuery.class);
    verify(metrics).query(actual.capture());
    assertThat(actual.getValue()).isSameAs(planned);
    var order = inOrder(permissions, metrics);
    order
        .verify(permissions, times(2))
        .require(OWNER, new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    order.verify(metrics).query(planned);
    assertThat(output.notices()).containsExactly("Coverage is unverified");
  }

  @Test
  void transitiveOperandDenialPreventsEveryRead() {
    GetMetricTool tool = new GetMetricTool(metrics, new WidgetRenderer(catalog), catalog);
    var input =
        new GetMetricInput(MetricId.LABOUR_COST_PER_COVER, DATE, DATE, TimeGrain.DAY, Set.of());
    doThrow(AccessDeniedException.forResource("reservations.metrics"))
        .when(permissions)
        .require(OWNER, new ResourceKey("reservations.metrics"), PermissionAction.READ);

    assertThatThrownBy(() -> dispatcher(tool).dispatch(tool.id(), input, OWNER))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(metrics);
  }

  private static TimeSeriesResult result(MetricQuery query) {
    return new TimeSeriesResult(
        query.metric(),
        List.of(new MetricSeries(null, List.of())),
        List.of("Coverage is unverified"),
        new MetricProvenance(
            query.metric(), "1", query.range(), query.grain(), "test", null, List.of(), "1"));
  }
}
