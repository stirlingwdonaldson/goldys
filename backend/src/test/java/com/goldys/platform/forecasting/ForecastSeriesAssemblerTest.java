package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.catalog.MetricPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastSeriesAssemblerTest {

  @Test
  void axisInclusiveDates() {
    List<LocalDate> axis =
        ForecastSeriesAssembler.axis(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));

    assertThat(axis)
        .containsExactly(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
  }

  @Test
  void alignNullsMissingDays() {
    List<LocalDate> axis =
        List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
    List<MetricPoint> points =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("10.0")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("30.0")));

    List<BigDecimal> aligned = ForecastSeriesAssembler.align(axis, points);

    assertThat(aligned).containsExactly(new BigDecimal("10.0"), null, new BigDecimal("30.0"));
  }

  @Test
  void alignIgnoresOutOfAxisPoints() {
    List<LocalDate> axis = List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
    List<MetricPoint> points =
        List.of(
            new MetricPoint(LocalDate.of(2026, 9, 30), new BigDecimal("5.0")),
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("10.0")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("30.0")));

    List<BigDecimal> aligned = ForecastSeriesAssembler.align(axis, points);

    assertThat(aligned).containsExactly(new BigDecimal("10.0"), null);
  }

  @Test
  void requestAlignsCovariates() {
    List<LocalDate> axis =
        List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
    List<MetricPoint> target =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("100.0")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("120.0")));
    List<CovariateSeries> covariates =
        List.of(
            new CovariateSeries(
                "reservations.covers",
                List.of(
                    new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("50")),
                    new MetricPoint(LocalDate.of(2026, 10, 2), new BigDecimal("55")))));
    int horizonDays = 30;
    List<String> quantiles = List.of("0.1", "0.5", "0.9");

    TimesFmRequest request =
        ForecastSeriesAssembler.request(axis, target, covariates, horizonDays, quantiles);

    assertThat(request.dates()).isEqualTo(axis);
    assertThat(request.target())
        .containsExactly(new BigDecimal("100.0"), null, new BigDecimal("120.0"));
    assertThat(request.covariates()).hasSize(1);
    TimesFmRequest.Covariate covariate = request.covariates().get(0);
    assertThat(covariate.name()).isEqualTo("reservations.covers");
    assertThat(covariate.values())
        .containsExactly(new BigDecimal("50"), new BigDecimal("55"), null);
    assertThat(request.horizonDays()).isEqualTo(30);
    assertThat(request.quantiles()).containsExactly("0.1", "0.5", "0.9");
  }
}
