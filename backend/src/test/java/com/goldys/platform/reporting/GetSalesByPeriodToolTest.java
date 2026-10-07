package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetSalesByPeriodToolTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void emitsResolvedPointsAndNoticesUnresolvedDates() {
    SalesMetricsQuery metrics = mock(SalesMetricsQuery.class);
    when(metrics.dailySales(SEP_13, SEP_14))
        .thenReturn(List.of(metric(SEP_13, "27650.66"), metric(SEP_14, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_14, Metric.GROSS_SALES), OWNER);

    assertThat(result.widget()).isInstanceOf(TimeSeriesWidgetSpec.class);
    var widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series()).hasSize(1);
    assertThat(widget.series().get(0).points()).hasSize(2);
    assertThat(widget.series().get(0).points().get(0).x()).isEqualTo("2026-09-13");
    assertThat(widget.series().get(0).points().get(0).y()).isEqualByComparingTo("27650.66");
    assertThat(widget.series().get(0).points().get(1).y()).isNull();
    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("no resolved total");
  }

  @Test
  void treatsMissingDataAsUnresolved() {
    SalesMetricsQuery metrics = mock(SalesMetricsQuery.class);
    when(metrics.dailySales(SEP_13, SEP_13)).thenReturn(List.of(metric(SEP_13, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics);
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_13, Metric.GROSS_SALES), OWNER);

    var widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series().get(0).points().get(0).y()).isNull();
    assertThat(result.notices()).hasSize(1);
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(() -> new GetSalesByPeriodInput(SEP_14, SEP_13, Metric.GROSS_SALES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsAWrongInputType() {
    SalesMetricsQuery metrics = mock(SalesMetricsQuery.class);
    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics);

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetSalesByPeriodInput");
  }

  private static DailySalesMetric metric(LocalDate date, String total) {
    return new DailySalesMetric(
        date, total == null ? null : new BigDecimal(total), null, null, null, total == null);
  }

  private record OtherInput() implements ToolInput {}
}
