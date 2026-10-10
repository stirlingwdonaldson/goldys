package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.PaymentMetricsQuery;
import com.goldys.platform.semantic.PaymentMix;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentMetricExecutorTest {

  @Test
  void sumsAmountAcrossTendersForADay() {
    PaymentMetricsQuery q = mock(PaymentMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.dailyMix(d, d))
        .thenReturn(
            List.of(
                new PaymentMix(d, "Tyro", new BigDecimal("222"), BigDecimal.ZERO, 4, false),
                new PaymentMix(d, "Cash", new BigDecimal("50"), new BigDecimal("3"), 1, false)));

    PaymentMetricExecutor executor = new PaymentMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.PAYMENTS_AMOUNT,
                new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("272.00");
  }

  @Test
  void sumsTipAcrossTendersForADay() {
    PaymentMetricsQuery q = mock(PaymentMetricsQuery.class);
    LocalDate d = LocalDate.of(2026, 9, 13);
    when(q.dailyMix(d, d))
        .thenReturn(
            List.of(
                new PaymentMix(d, "Tyro", new BigDecimal("222"), new BigDecimal("1"), 4, false)));

    PaymentMetricExecutor executor = new PaymentMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.PAYMENTS_TIP,
                new TimeRange(d, d, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("1");
  }
}
