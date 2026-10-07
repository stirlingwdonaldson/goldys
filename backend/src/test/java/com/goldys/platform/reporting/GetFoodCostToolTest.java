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

class GetFoodCostToolTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 20);
  private static final LocalDate TO = LocalDate.of(2026, 9, 20);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private static final WidgetRenderer RENDERER = new WidgetRenderer(new MetricCatalog());

  @Test
  void emitsResolvedFoodCostTable() {
    MetricQueryService metrics = mock(MetricQueryService.class);
    when(metrics.query(any()))
        .thenAnswer(
            inv -> {
              MetricId metric = ((MetricQuery) inv.getArgument(0)).metric();
              if (metric == MetricId.INVENTORY_PURCHASES) {
                return tsResult(MetricId.INVENTORY_PURCHASES, List.of(), point(FROM, "70.00"));
              }
              if (metric == MetricId.INVENTORY_WASTAGE) {
                return tsResult(MetricId.INVENTORY_WASTAGE, List.of(), point(FROM, null));
              }
              throw new IllegalArgumentException("unexpected metric " + metric);
            });

    GetFoodCostTool tool = new GetFoodCostTool(metrics, RENDERER);
    ToolResult result = tool.execute(new GetFoodCostInput(FROM, TO), OWNER);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat((BigDecimal) widget.rows().get(0).get("inventory.purchases"))
        .isEqualByComparingTo(new BigDecimal("70.00"));
    assertThat(widget.rows().get(0).get("inventory.wastage")).isNull();
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    assertThatThrownBy(
            () -> new GetFoodCostInput(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void rejectsWrongInputType() {
    GetFoodCostTool tool = new GetFoodCostTool(mock(MetricQueryService.class), RENDERER);

    assertThatThrownBy(() -> tool.execute(new OtherInput(), OWNER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetFoodCostInput");
  }

  private static TimeSeriesResult tsResult(
      MetricId id, List<String> notices, MetricPoint... points) {
    MetricProvenance provenance =
        new MetricProvenance(
            id,
            "1",
            new TimeRange(FROM, TO, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_inventory_day",
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
