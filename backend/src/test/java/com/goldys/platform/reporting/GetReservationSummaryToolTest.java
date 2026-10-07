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

class GetReservationSummaryToolTest {

  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static final WidgetRenderer RENDERER = new WidgetRenderer(new MetricCatalog());

  @Test
  void emitsResolvedSummaryTable() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricId metric = ((MetricQuery) inv.getArgument(0)).metric();
              return switch (metric) {
                case RESERVATIONS_BOOKINGS -> ts(metric, "10");
                case RESERVATIONS_ATTENDED -> ts(metric, "8");
                case RESERVATIONS_COVERS -> ts(metric, "32");
                case RESERVATIONS_NO_SHOWS -> ts(metric, "1");
                default -> throw new IllegalArgumentException("unexpected metric " + metric);
              };
            });

    GetReservationSummaryTool tool = new GetReservationSummaryTool(metrics, RENDERER);
    ToolResult result = tool.execute(new GetReservationSummaryInput(SEP_20), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.columns()).hasSize(4);
    assertThat(widget.rows()).hasSize(1);
    assertThat((BigDecimal) widget.rows().get(0).get("reservations.covers"))
        .isEqualByComparingTo(new BigDecimal("32"));
    assertThat(result.notices()).isEmpty();
  }

  @Test
  void emitsNoticeWhenThereIsNoData() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> ts(((MetricQuery) inv.getArgument(0)).metric(), List.of("1 day(s) unresolved")));

    GetReservationSummaryTool tool = new GetReservationSummaryTool(metrics, RENDERER);
    ToolResult result = tool.execute(new GetReservationSummaryInput(SEP_20), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    assertThat(result.notices()).hasSize(4);
    assertThat(result.notices().get(0)).contains("unresolved");
  }

  @Test
  void rejectsWrongInputType() {
    GetReservationSummaryTool tool =
        new GetReservationSummaryTool(mock(MetricQueryService.class), RENDERER);

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetReservationSummaryInput");
  }

  private static TimeSeriesResult ts(MetricId id, String value) {
    return ts(id, List.of(), new MetricPoint(SEP_20, value == null ? null : new BigDecimal(value)));
  }

  private static TimeSeriesResult ts(MetricId id, List<String> notices) {
    return ts(id, notices, new MetricPoint(SEP_20, null));
  }

  private static TimeSeriesResult ts(MetricId id, List<String> notices, MetricPoint point) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(SEP_20, SEP_20, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_reservation_day",
            Instant.EPOCH,
            List.of(),
            "1");
    return new TimeSeriesResult(
        id, List.of(new MetricSeries(null, List.of(point))), notices, provenance);
  }

  private record OtherInput() implements ToolInput {}
}
