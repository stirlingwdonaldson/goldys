package com.goldys.platform.semantic.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.MissingDataStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LabourMetricExecutorTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);

  @Test
  void sumsLabourCostAcrossTheRange() {
    LabourMetricsQuery q = mock(LabourMetricsQuery.class);
    when(q.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13,
                    "KITCHEN",
                    new BigDecimal("10.00"),
                    new BigDecimal("9.00"),
                    new BigDecimal("200.00"),
                    new BigDecimal("1500.00"),
                    "agreed",
                    false)));

    LabourMetricExecutor executor = new LabourMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.LABOUR_COST,
                new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("1500.00");
  }

  @Test
  void sumsActualHoursAcrossDepartmentsForADay() {
    LabourMetricsQuery q = mock(LabourMetricsQuery.class);
    when(q.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13,
                    "KITCHEN",
                    new BigDecimal("10.00"),
                    new BigDecimal("9.00"),
                    new BigDecimal("200.00"),
                    new BigDecimal("1500.00"),
                    "agreed",
                    false),
                new LabourMetric(
                    SEP_13,
                    "FLOOR",
                    new BigDecimal("12.00"),
                    new BigDecimal("11.00"),
                    new BigDecimal("240.00"),
                    new BigDecimal("1800.00"),
                    "agreed",
                    false)));

    LabourMetricExecutor executor = new LabourMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.LABOUR_ACTUAL_HOURS,
                new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    assertThat(((TimeSeriesResult) result).series().get(0).points().get(0).value())
        .isEqualByComparingTo("20.00");
  }

  @Test
  void flagsADayMissingWhenAnyDepartmentIsUnresolved() {
    LabourMetricsQuery q = mock(LabourMetricsQuery.class);
    when(q.dailyLabour(SEP_13, SEP_13))
        .thenReturn(
            List.of(
                new LabourMetric(
                    SEP_13,
                    "FOH",
                    new BigDecimal("8.00"),
                    new BigDecimal("8.00"),
                    new BigDecimal("200.00"),
                    new BigDecimal("100.00"),
                    "agreed",
                    false),
                new LabourMetric(
                    SEP_13,
                    "BOH",
                    new BigDecimal("4.00"),
                    new BigDecimal("4.00"),
                    new BigDecimal("100.00"),
                    null,
                    "agreed",
                    false)));

    LabourMetricExecutor executor = new LabourMetricExecutor(q, new MetricCatalog());
    MetricResult result =
        executor.evaluate(
            new MetricQuery(
                MetricId.LABOUR_COST,
                new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));

    TimeSeriesResult ts = (TimeSeriesResult) result;
    assertThat(ts.series().get(0).points().get(0).value()).isNull();
    assertThat(ts.series().get(0).points().get(0).status()).isEqualTo(MissingDataStatus.UNRESOLVED);
    assertThat(ts.notices()).containsExactly("1 day(s) unresolved");
    assertThat(ts.provenance().missingPeriods()).containsExactly(SEP_13);
  }
}
