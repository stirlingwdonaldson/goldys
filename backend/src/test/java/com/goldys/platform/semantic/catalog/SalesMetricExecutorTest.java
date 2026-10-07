package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.DailySalesMetric;
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
    assertThat(ts.series().get(0).points().get(1).value()).isNull();
    assertThat(ts.notices()).containsExactly("1 day(s) unresolved");
    assertThat(ts.provenance().missingPeriods()).containsExactly(SEP_14);
  }

  private static DailySalesMetric metric(LocalDate d, String gross, String net, String gst) {
    return new DailySalesMetric(d, big(gross), big(net), big(gst), "agreed", gross == null);
  }

  private static BigDecimal big(String s) {
    return s == null ? null : new BigDecimal(s);
  }
}
