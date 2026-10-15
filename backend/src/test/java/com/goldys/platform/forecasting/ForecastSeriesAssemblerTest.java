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
  void requestCarriesBookedCoversAndFoodBev() {
    List<LocalDate> axis =
        List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
    List<MetricPoint> net =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("100.0")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("120.0")));
    List<MetricPoint> bookedCovers =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("50")),
            new MetricPoint(LocalDate.of(2026, 10, 2), new BigDecimal("55")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("60")));
    List<MetricPoint> bookings =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("30")),
            new MetricPoint(LocalDate.of(2026, 10, 2), new BigDecimal("32")));
    List<MetricPoint> food =
        List.of(new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("45.0")));
    List<MetricPoint> bev =
        List.of(
            new MetricPoint(LocalDate.of(2026, 10, 1), new BigDecimal("55.0")),
            new MetricPoint(LocalDate.of(2026, 10, 3), new BigDecimal("65.0")));
    int horizon = 30;
    List<String> quantiles = List.of("0.1", "0.5", "0.9");

    ForecastModelRequest request =
        ForecastSeriesAssembler.request(
            axis, net, bookedCovers, bookings, food, bev, horizon, quantiles);

    assertThat(request.dates()).isEqualTo(axis);
    assertThat(request.net())
        .containsExactly(new BigDecimal("100.0"), null, new BigDecimal("120.0"));
    assertThat(request.bookedCovers())
        .containsExactly(new BigDecimal("50"), new BigDecimal("55"), new BigDecimal("60"));
    assertThat(request.bookings())
        .containsExactly(new BigDecimal("30"), new BigDecimal("32"), null);
    assertThat(request.food()).containsExactly(new BigDecimal("45.0"), null, null);
    assertThat(request.bev()).containsExactly(new BigDecimal("55.0"), null, new BigDecimal("65.0"));
    assertThat(request.horizon()).isEqualTo(30);
    assertThat(request.quantiles()).containsExactly("0.1", "0.5", "0.9");
  }
}
