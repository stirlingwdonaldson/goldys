package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.InventoryMetric;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryMetricExecutorTest {

  @Test
  void sumsPurchases() {
    InventoryMetricsQuery q = mock(InventoryMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.dailyInventory(d, d))
        .thenReturn(
            List.of(
                new InventoryMetric(
                    d,
                    new BigDecimal("800.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("5000.00"),
                    "agreed",
                    false)));

    InventoryMetricExecutor executor = new InventoryMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.INVENTORY_PURCHASES,
                new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("800.00");
  }
}
