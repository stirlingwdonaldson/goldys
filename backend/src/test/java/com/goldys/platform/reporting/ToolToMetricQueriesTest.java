package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ToolToMetricQueriesTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);

  private static MetricQueryService mockMetricQueryService() {
    return mock(MetricQueryService.class);
  }

  private static WidgetRenderer renderer() {
    return new WidgetRenderer(new MetricCatalog());
  }

  @Test
  void getSalesByPeriodBridgesToMetricQuery() {
    GetSalesByPeriodTool tool = new GetSalesByPeriodTool(mockMetricQueryService(), renderer());
    var input = new GetSalesByPeriodInput(SEP_13, SEP_13, MetricId.SALES_GROSS);

    var qs = tool.toMetricQueries(input);

    assertThat(qs).hasSize(1);
    assertThat(qs.get(0).metric()).isEqualTo(MetricId.SALES_GROSS);
    assertThat(qs.get(0).grain()).isEqualTo(TimeGrain.DAY);
  }

  @Test
  void getLabourCostBridgesToFourQueries() {
    GetLabourCostTool tool = new GetLabourCostTool(mockMetricQueryService(), renderer());
    var input = new GetLabourCostInput(SEP_13, SEP_20);

    var qs = tool.toMetricQueries(input);

    assertThat(qs).hasSize(4);
    assertThat(qs.stream().map(MetricQuery::metric).toList())
        .containsExactly(
            MetricId.LABOUR_SCHEDULED_HOURS,
            MetricId.LABOUR_ACTUAL_HOURS,
            MetricId.LABOUR_COST,
            MetricId.LABOUR_HOURS_VARIANCE);
    assertThat(qs.get(0).range().calendar()).isEqualTo(Calendar.CALENDAR);
    assertThat(qs.get(0).grain()).isEqualTo(TimeGrain.DAY);
  }

  @Test
  void getFoodCostBridgesToTwoQueries() {
    GetFoodCostTool tool = new GetFoodCostTool(mockMetricQueryService(), renderer());
    var input = new GetFoodCostInput(SEP_13, SEP_20);

    var qs = tool.toMetricQueries(input);

    assertThat(qs).hasSize(2);
    assertThat(qs.stream().map(MetricQuery::metric).toList())
        .containsExactly(MetricId.INVENTORY_PURCHASES, MetricId.INVENTORY_WASTAGE);
  }

  @Test
  void getReservationSummaryBridgesToFourQueriesOverSingleDate() {
    GetReservationSummaryTool tool =
        new GetReservationSummaryTool(mockMetricQueryService(), renderer());
    var input = new GetReservationSummaryInput(SEP_20);

    var qs = tool.toMetricQueries(input);

    assertThat(qs).hasSize(4);
    assertThat(qs.stream().map(MetricQuery::metric).toList())
        .containsExactly(
            MetricId.RESERVATIONS_BOOKINGS,
            MetricId.RESERVATIONS_ATTENDED,
            MetricId.RESERVATIONS_COVERS,
            MetricId.RESERVATIONS_NO_SHOWS);
    assertThat(qs.get(0).range().from()).isEqualTo(SEP_20);
    assertThat(qs.get(0).range().to()).isEqualTo(SEP_20);
  }
}
