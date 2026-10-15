package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.FoodBevSplit;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FoodBevMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void foodBevDailySeries() {
    ProductCategoryMetricsQuery q = mock(ProductCategoryMetricsQuery.class);
    LocalDate sep14 = SEP_13.plusDays(1);
    when(q.dailyFoodBev(SEP_13, sep14))
        .thenReturn(
            List.of(
                new FoodBevSplit(SEP_13, new BigDecimal("300.00"), new BigDecimal("200.00")),
                new FoodBevSplit(sep14, new BigDecimal("150.00"), new BigDecimal("250.00"))));

    FoodBevMetricExecutor executor = new FoodBevMetricExecutor(q, new MetricCatalog());

    TimeSeriesResult food =
        (TimeSeriesResult)
            executor.evaluate(
                new MetricQuery(
                    MetricId.SALES_FOOD,
                    new TimeRange(SEP_13, sep14, Calendar.CALENDAR),
                    TimeGrain.DAY,
                    Set.of(),
                    null));
    assertThat(food.series()).hasSize(1);
    assertThat(food.series().get(0).points().get(0).value()).isEqualByComparingTo("300.00");
    assertThat(food.series().get(0).points().get(1).value()).isEqualByComparingTo("150.00");

    TimeSeriesResult bev =
        (TimeSeriesResult)
            executor.evaluate(
                new MetricQuery(
                    MetricId.SALES_BEV,
                    new TimeRange(SEP_13, sep14, Calendar.CALENDAR),
                    TimeGrain.DAY,
                    Set.of(),
                    null));
    assertThat(bev.series()).hasSize(1);
    assertThat(bev.series().get(0).points().get(0).value()).isEqualByComparingTo("200.00");
    assertThat(bev.series().get(0).points().get(1).value()).isEqualByComparingTo("250.00");
  }
}
