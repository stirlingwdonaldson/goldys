package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricQueryServiceImplTest {

  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), Calendar.CALENDAR);

  @Test
  void delegatesToTheExecutorForTheMetric() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.ids()).thenReturn(Set.of(MetricId.SALES_GROSS));
    MetricQuery query = new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null);
    MetricResult expected =
        new TimeSeriesResult(
            MetricId.SALES_GROSS,
            List.of(new MetricSeries(null, List.of(new MetricPoint(RANGE.from(), null)))),
            List.of(),
            new MetricProvenance(
                MetricId.SALES_GROSS,
                "1",
                RANGE,
                TimeGrain.DAY,
                "resolved_daily_sales",
                Instant.EPOCH,
                List.of(),
                "1"));
    when(executor.evaluate(query)).thenReturn(expected);

    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThat(service.query(query)).isSameAs(expected);
    verify(executor).evaluate(query);
  }

  @Test
  void rejectsAnUnknownMetric() {
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of());
    assertThatThrownBy(
            () -> service.query(new MetricQuery(null, RANGE, TimeGrain.DAY, Set.of(), null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnInvalidGrain() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.ids()).thenReturn(Set.of(MetricId.INVENTORY_STOCK_ON_HAND));
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThatThrownBy(
            () ->
                service.query(
                    new MetricQuery(
                        MetricId.INVENTORY_STOCK_ON_HAND, RANGE, TimeGrain.MONTH, Set.of(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("grain");
  }

  @Test
  void rejectsADimensionNotAllowedForTheMetric() {
    MetricExecutor executor = mock(MetricExecutor.class);
    when(executor.ids()).thenReturn(Set.of(MetricId.SALES_GROSS));
    MetricQueryService service = new MetricQueryServiceImpl(new MetricCatalog(), List.of(executor));

    assertThatThrownBy(
            () ->
                service.query(
                    new MetricQuery(
                        MetricId.SALES_GROSS,
                        RANGE,
                        TimeGrain.DAY,
                        Set.of(Dimension.DEPARTMENT),
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dimension");
  }
}
