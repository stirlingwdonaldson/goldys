package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.MissingDataStatus;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductMetricExecutorTest {

  @Test
  void productGroupingSeparatesAmountsAndQuantitiesAndPreservesMissingDays() {
    ProductMetricsQuery source = mock(ProductMetricsQuery.class);
    LocalDate day = LocalDate.of(2026, 9, 13);
    when(source.productSales(day, day.plusDays(1)))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    day, "Burger", BigDecimal.ZERO, BigDecimal.ZERO, "source", false),
                new ProductSalesMetric(
                    day.plusDays(1),
                    "Burger",
                    new BigDecimal("2"),
                    new BigDecimal("40"),
                    "source",
                    false),
                new ProductSalesMetric(
                    day, "Beer", new BigDecimal("3"), new BigDecimal("30"), "source", false)));
    var executor = new ProductMetricExecutor(source, new MetricCatalog());
    var range = new TimeRange(day, day.plusDays(1), Calendar.CALENDAR);
    var amounts =
        (TimeSeriesResult)
            executor.evaluate(
                new MetricQuery(
                    MetricId.PRODUCT_SALES_AMOUNT,
                    range,
                    TimeGrain.TOTAL,
                    Set.of(Dimension.PRODUCT),
                    null));
    assertThat(amounts.series())
        .extracting(MetricSeries::dimensionValue)
        .containsExactly("Burger", "Beer");
    assertThat(amounts.series().get(0).points().get(0).value()).isEqualByComparingTo("40");
    assertThat(amounts.series().get(1).points().get(0).value()).isEqualByComparingTo("30");
    assertThat(amounts.provenance().missingPeriods()).containsExactly(day.plusDays(1));
    assertThat(amounts.notices())
        .containsExactly(
            "Product totals use recorded sales; days without product observations are not assumed to be zero.");
    var quantities =
        (TimeSeriesResult)
            executor.evaluate(
                new MetricQuery(
                    MetricId.PRODUCT_SALES_QUANTITY,
                    range,
                    TimeGrain.DAY,
                    Set.of(Dimension.PRODUCT),
                    null));
    assertThat(quantities.series().get(0).points().get(0).value()).isEqualByComparingTo("0");
    assertThat(quantities.series().get(0).points().get(1).value()).isEqualByComparingTo("2");
    assertThat(quantities.series().get(1).points().get(0).value()).isEqualByComparingTo("3");
    assertThat(quantities.series().get(1).points().get(1).value()).isNull();
  }

  @Test
  void manySparseProductsProduceOneQualificationAndKeepUnavailablePoints() {
    ProductMetricsQuery source = mock(ProductMetricsQuery.class);
    LocalDate day = LocalDate.of(2026, 9, 13);
    when(source.productSales(day, day.plusDays(1)))
        .thenReturn(
            java.util.stream.IntStream.range(0, 100)
                .mapToObj(
                    i ->
                        new ProductSalesMetric(
                            day.plusDays(i % 2),
                            "Product " + i,
                            BigDecimal.ONE,
                            BigDecimal.TEN,
                            "single",
                            false))
                .toList());
    var result =
        (TimeSeriesResult)
            new ProductMetricExecutor(source, new MetricCatalog())
                .evaluate(
                    new MetricQuery(
                        MetricId.PRODUCT_SALES_AMOUNT,
                        new TimeRange(day, day.plusDays(1), Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(Dimension.PRODUCT),
                        null));
    assertThat(result.series()).hasSize(100);
    assertThat(result.notices()).singleElement().asString().contains("not assumed to be zero");
    assertThat(result.notices())
        .noneMatch(n -> n.contains("Incomplete") || n.contains("unresolved"));
    assertThat(result.series().getFirst().points().get(1).value()).isNull();
    assertThat(result.series().getFirst().points().get(1).status())
        .isEqualTo(MissingDataStatus.NOT_RECEIVED);
    assertThat(result.provenance().missingPeriods()).containsExactly(day, day.plusDays(1));
  }

  @Test
  void groupedProductsRetainDatasetGapsAndUnresolvedWarnings() {
    ProductMetricsQuery source = mock(ProductMetricsQuery.class);
    LocalDate day = LocalDate.of(2026, 9, 13);
    when(source.productSales(day, day.plusDays(2)))
        .thenReturn(
            List.of(
                new ProductSalesMetric(
                    day, "Burger", BigDecimal.ONE, BigDecimal.TEN, "single", false),
                new ProductSalesMetric(day.plusDays(1), "Beer", null, null, null, true)));
    var result =
        (TimeSeriesResult)
            new ProductMetricExecutor(source, new MetricCatalog())
                .evaluate(
                    new MetricQuery(
                        MetricId.PRODUCT_SALES_AMOUNT,
                        new TimeRange(day, day.plusDays(2), Calendar.CALENDAR),
                        TimeGrain.DAY,
                        Set.of(Dimension.PRODUCT),
                        null));
    assertThat(result.notices())
        .hasSize(2)
        .anySatisfy(
            n ->
                assertThat(n)
                    .contains(
                        "Incomplete subtotal",
                        "1 unresolved day(s)",
                        "1 day(s) without a resolved record"));
    assertThat(result.series().get(1).points().get(1).status())
        .isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(result.series().getFirst().points().get(2).value()).isNull();
  }

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
    assertThat(result.notices()).isEmpty();
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
    assertThat(ts.series().get(0).points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices())
        .anySatisfy(
            note -> assertThat(note).contains("Incomplete subtotal", "1 unresolved day(s)"));
    assertThat(ts.provenance().missingPeriods()).containsExactly(d);
  }
}
