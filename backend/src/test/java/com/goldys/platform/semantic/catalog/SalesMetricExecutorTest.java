package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.MissingDataStatus;
import com.goldys.platform.semantic.SalesMetricsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Test
  void sumsGrossSalesAndNoticesUnresolvedDays() {
    SalesMetricsQuery q = mock(SalesMetricsQuery.class);
    when(q.dailySales(SEP_13, SEP_14))
        .thenReturn(
            List.of(metric(SEP_13, "100.00", "90.91", "9.09"), metric(SEP_14, null, null, null)));

    SalesMetricExecutor executor = new SalesMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.SALES_GROSS,
                new TimeRange(SEP_13, SEP_14, Calendar.CALENDAR),
                TimeGrain.DAY,
                java.util.Set.of(),
                null));

    TimeSeriesResult ts = (TimeSeriesResult) result;
    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("100.00");
    assertThat(ts.series().get(0).points().get(0).status()).isNull();
    assertThat(ts.series().get(0).points().get(1).value()).isNull();
    assertThat(ts.series().get(0).points().get(1).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices())
        .anySatisfy(
            note -> assertThat(note).contains("Incomplete subtotal", "1 unresolved day(s)"));
    assertThat(ts.provenance().missingPeriods()).containsExactly(SEP_14);
  }

  @Test
  void historicalSubtotalRetainsDistinctMissingReasonsAndExplicitZero() {
    LocalDate day = LocalDate.of(2020, 1, 6);
    SalesMetricsQuery q = mock(SalesMetricsQuery.class);
    when(q.dailySales(day, day.plusDays(3)))
        .thenReturn(
            List.of(
                metric(day, "100", "100", "0"),
                metric(day.plusDays(1), "0", "0", "0"),
                metric(day.plusDays(2), null, null, null)));
    TimeSeriesResult result =
        (TimeSeriesResult)
            new SalesMetricExecutor(q, new MetricCatalog())
                .evaluate(
                    new MetricQuery(
                        MetricId.SALES_NET,
                        new TimeRange(day, day.plusDays(3), Calendar.CALENDAR),
                        TimeGrain.TOTAL,
                        java.util.Set.of(),
                        null));
    assertThat(result.series().getFirst().points().getFirst().value()).isEqualByComparingTo("100");
    assertThat(result.provenance().coverage().observedDates())
        .containsExactly(day, day.plusDays(1));
    assertThat(result.provenance().coverage().unresolvedDates()).containsExactly(day.plusDays(2));
    assertThat(result.provenance().coverage().absentDates()).containsExactly(day.plusDays(3));
    assertThat(result.notices())
        .anySatisfy(
            note ->
                assertThat(note)
                    .contains("Incomplete subtotal", "1 unresolved", "1 day(s) without"));
  }

  private static DailySalesMetric metric(LocalDate d, String gross, String net, String gst) {
    return new DailySalesMetric(d, big(gross), big(net), big(gst), "agreed", gross == null);
  }

  private static BigDecimal big(String s) {
    return s == null ? null : new BigDecimal(s);
  }
}
