package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.MissingDataStatus;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReservationMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void sumsCoversPerDay() {
    ReservationMetricsQuery q = mock(ReservationMetricsQuery.class);
    when(q.dailySummaries(SEP_13, SEP_13))
        .thenReturn(List.of(new ReservationSummary(SEP_13, 10, 8, 120, 1, 2, 3, null, null, null)));

    ReservationMetricExecutor executor = new ReservationMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.RESERVATIONS_COVERS,
                new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo(new BigDecimal("120"));
  }

  @Test
  void picksTheFieldForEachMetricAndFlagsMissingDays() {
    ReservationMetricsQuery q = mock(ReservationMetricsQuery.class);
    LocalDate sep14 = SEP_13.plusDays(1);
    when(q.dailySummaries(SEP_13, sep14))
        .thenReturn(List.of(new ReservationSummary(SEP_13, 10, 8, 120, 1, 2, 3, null, null, null)));

    ReservationMetricExecutor executor = new ReservationMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.RESERVATIONS_NO_SHOWS,
                new TimeRange(SEP_13, sep14, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    TimeSeriesResult ts = (TimeSeriesResult) result;
    assertThat(ts.series().get(0).points().get(0).value()).isEqualByComparingTo("2");
    assertThat(ts.series().get(0).points().get(1).value()).isNull();
    assertThat(ts.series().get(0).points().get(1).status())
        .isEqualTo(MissingDataStatus.NOT_RECEIVED);
    assertThat(ts.notices()).containsExactly("1 day(s) unresolved");
  }
}
