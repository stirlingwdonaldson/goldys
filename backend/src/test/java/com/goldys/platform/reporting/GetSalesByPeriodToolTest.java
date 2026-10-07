package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
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
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(
            tsResult(
                MetricId.SALES_GROSS,
                List.of("1 day(s) unresolved"),
                point(SEP_13, "27650.66"),
                point(SEP_14, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics, new MetricCatalog());
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_14, MetricId.SALES_GROSS), OWNER);

    assertThat(result.widget()).isInstanceOf(TimeSeriesWidgetSpec.class);
    var widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series()).hasSize(1);
    assertThat(widget.series().get(0).points()).hasSize(2);
    assertThat(widget.series().get(0).points().get(0).x()).isEqualTo("2026-09-13");
    assertThat(widget.series().get(0).points().get(0).y()).isEqualByComparingTo("27650.66");
    assertThat(widget.series().get(0).points().get(1).y()).isNull();
    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("unresolved");
  }

  @Test
  void treatsMissingDataAsUnresolved() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenReturn(
            tsResult(MetricId.SALES_GROSS, List.of("1 day(s) unresolved"), point(SEP_13, null)));

    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics, new MetricCatalog());
    ToolResult result =
        tool.execute(new GetSalesByPeriodInput(SEP_13, SEP_13, MetricId.SALES_GROSS), OWNER);

    var widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series().get(0).points().get(0).y()).isNull();
    assertThat(result.notices()).hasSize(1);
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(() -> new GetSalesByPeriodInput(SEP_14, SEP_13, MetricId.SALES_GROSS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsAWrongInputType() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics, new MetricCatalog());

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetSalesByPeriodInput");
  }

  @Test
  void rejectsANonSalesMetric() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(metrics, new MetricCatalog());

    assertThatThrownBy(
            () ->
                tool.execute(
                    new GetSalesByPeriodInput(SEP_13, SEP_13, MetricId.PRODUCT_TOP_SELLERS), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported metric");
    verifyNoInteractions(metrics);
  }

  private static TimeSeriesResult tsResult(
      MetricId id, List<String> notices, MetricPoint... points) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(SEP_13, SEP_14, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            Instant.EPOCH,
            List.of(),
            "1");
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of(points))), notices, provenance);
  }

  private static MetricPoint point(LocalDate date, String total) {
    return new MetricPoint(date, total == null ? null : new BigDecimal(total));
  }

  private record OtherInput() implements ToolInput {}
}
