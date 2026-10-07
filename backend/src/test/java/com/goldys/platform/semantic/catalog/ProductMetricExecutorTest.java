package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductMetricExecutorTest {

  @Test
  void sumsProductAmountAcrossProductsForADay() {
    ProductMetricsQuery q = mock(ProductMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.productSales(d, d))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    d, "Burger", new BigDecimal("3"), new BigDecimal("60.00"), "agreed", false),
                new ProductSalesMetric(
                    d, "Chips", new BigDecimal("5"), new BigDecimal("25.00"), "agreed", false)));

    ProductMetricExecutor executor = new ProductMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.PRODUCT_SALES_AMOUNT,
                new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("85.00");
  }

  @Test
  void flagsADayMissingWhenAnyProductIsUnresolved() {
    ProductMetricsQuery q = mock(ProductMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.productSales(d, d))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    d, "Burger", new BigDecimal("3"), new BigDecimal("60.00"), "agreed", false),
                new ProductSalesMetric(d, "Chips", new BigDecimal("5"), null, "agreed", false)));

    ProductMetricExecutor executor = new ProductMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.PRODUCT_SALES_AMOUNT,
                new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    TimeSeriesResult ts = (TimeSeriesResult) result;
    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.notices()).containsExactly("1 day(s) unresolved");
    assertThat(ts.provenance().missingPeriods()).containsExactly(d);
  }
}
