package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetLabourVarianceToolTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static final WidgetRenderer RENDERER = new WidgetRenderer(new MetricCatalog());

  @Test
  void exposesVarianceNameAndBroadenedMetricQueries() {
    GetLabourVarianceTool tool =
        new GetLabourVarianceTool(mock(MetricQueryService.class), RENDERER);

    assertThat(tool.name()).isEqualTo("get_labour_variance");
    assertThat(tool.toMetricQueries(new GetLabourVarianceInput(FROM, TO)))
        .extracting(MetricQuery::metric)
        .contains(MetricId.LABOUR_FOH_PERCENT, MetricId.LABOUR_BOH_PERCENT);
  }

  @Test
  void emitsResolvedLabourTable() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricId metric = ((MetricQuery) inv.getArgument(0)).metric();
              return switch (metric) {
                case LABOUR_SCHEDULED_HOURS -> tsResult(metric, List.of(), point(FROM, "14.00"));
                case LABOUR_ACTUAL_HOURS -> tsResult(metric, List.of(), point(FROM, "13.50"));
                case LABOUR_COST -> tsResult(metric, List.of(), point(FROM, "350.00"));
                case LABOUR_HOURS_VARIANCE -> tsResult(metric, List.of(), point(FROM, "0.50"));
                case LABOUR_FOH_PERCENT -> tsResult(metric, List.of(), point(FROM, "30.00"));
                case LABOUR_BOH_PERCENT -> tsResult(metric, List.of(), point(FROM, "20.00"));
                default -> throw new IllegalArgumentException("unexpected metric " + metric);
              };
            });

    GetLabourVarianceTool tool = new GetLabourVarianceTool(metrics, RENDERER);
    ToolResult result = tool.execute(new GetLabourVarianceInput(FROM, TO), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.rows()).hasSize(1);
    assertThat((BigDecimal) widget.rows().get(0).get("labour.cost"))
        .isEqualByComparingTo(new BigDecimal("350.00"));
    assertThat(result.notices()).isEmpty();
  }

  @Test
  void emitsNoticeWhenCostIsUnknown() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricId metric = ((MetricQuery) inv.getArgument(0)).metric();
              return switch (metric) {
                case LABOUR_SCHEDULED_HOURS -> tsResult(metric, List.of(), point(FROM, "14.00"));
                case LABOUR_ACTUAL_HOURS -> tsResult(metric, List.of(), point(FROM, "13.50"));
                case LABOUR_COST ->
                    tsResult(metric, List.of("1 day(s) unresolved"), point(FROM, null));
                case LABOUR_HOURS_VARIANCE -> tsResult(metric, List.of(), point(FROM, "0.50"));
                case LABOUR_FOH_PERCENT -> tsResult(metric, List.of(), point(FROM, "30.00"));
                case LABOUR_BOH_PERCENT -> tsResult(metric, List.of(), point(FROM, "20.00"));
                default -> throw new IllegalArgumentException("unexpected metric " + metric);
              };
            });

    GetLabourVarianceTool tool = new GetLabourVarianceTool(metrics, RENDERER);
    ToolResult result = tool.execute(new GetLabourVarianceInput(FROM, TO), OWNER);

    assertThat(result.notices()).hasSize(1);
    assertThat(result.notices().get(0)).contains("unresolved");
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(
            () -> new GetLabourVarianceInput(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsWrongInputType() {
    GetLabourVarianceTool tool =
        new GetLabourVarianceTool(mock(MetricQueryService.class), RENDERER);

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetLabourVarianceInput");
  }

  private static TimeSeriesResult tsResult(
      MetricId id, List<String> notices, MetricPoint... points) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(FROM, TO, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_labour_day",
            Instant.EPOCH,
            List.of(),
            "1");
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of(points))), notices, provenance);
  }

  private static MetricPoint point(LocalDate date, String value) {
    return new MetricPoint(date, value == null ? null : new BigDecimal(value));
  }

  private record OtherInput() implements ToolInput {}
}
