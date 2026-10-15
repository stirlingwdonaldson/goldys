package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForecastRunTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:15:30Z");
  private static final String CREATED_BY = "owner@example.com";

  @Test
  void createYieldsQueuedRunWithGivenFields() {
    List<String> covariates = List.of("reservations.covers", "labour.cost");

    ForecastRun run =
        ForecastRun.create(
            "sales.gross",
            covariates,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 10, 11),
            30,
            CREATED_BY,
            NOW);

    assertThat(run.id()).isNotNull();
    assertThat(run.status()).isEqualTo(ForecastRunStatus.QUEUED);
    assertThat(run.targetMetric()).isEqualTo("sales.gross");
    assertThat(run.targetDimension()).isNull();
    assertThat(run.covariates()).containsExactlyElementsOf(covariates);
    assertThat(run.historyFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(run.historyTo()).isEqualTo(LocalDate.of(2026, 10, 11));
    assertThat(run.horizonDays()).isEqualTo(30);
    assertThat(run.grain()).isEqualTo("DAY");
    assertThat(run.createdBy()).isEqualTo(CREATED_BY);
    assertThat(run.createdAt()).isEqualTo(NOW);
    assertThat(run.series()).isEmpty();
    assertThat(run.errorMessage()).isNull();
    assertThat(run.modelVersion()).isNull();
    assertThat(run.elapsedMs()).isNull();
  }

  @Test
  void markRunningTransitionsStatus() {
    ForecastRun run = run();

    run.markRunning();

    assertThat(run.status()).isEqualTo(ForecastRunStatus.RUNNING);
  }

  @Test
  void markSucceededStoresSeriesModelVersionAndElapsedMs() {
    ForecastRun run = run();
    List<ForecastDay> series =
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 12),
                Map.of(
                    "0.1", new BigDecimal("100.5"),
                    "0.5", new BigDecimal("120.0"),
                    "0.9", new BigDecimal("140.5"))));

    run.markSucceeded(series, "timesfm-3.0", 42L);

    assertThat(run.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    assertThat(run.series()).containsExactlyElementsOf(series);
    assertThat(run.modelVersion()).isEqualTo("timesfm-3.0");
    assertThat(run.elapsedMs()).isEqualTo(42L);
    assertThat(run.diagnostics()).isNull();
  }

  @Test
  void markSucceededStoresDiagnostics() {
    ForecastRun run = run();
    List<ForecastDay> series =
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 12),
                Map.of(
                    "0.1", new BigDecimal("100.5"),
                    "0.5", new BigDecimal("120.0"),
                    "0.9", new BigDecimal("140.5")),
                new BigDecimal("58.0"),
                new BigDecimal("62.0"),
                new BigDecimal("115.0"),
                new BigDecimal("142"),
                true,
                "Melbourne Cup",
                false,
                false,
                false,
                true,
                new BigDecimal("22.5"),
                new BigDecimal("0.0"),
                "open-meteo",
                new BigDecimal("60")));
    String diagnosticsJson = "{\"modelVersion\":\"timesfm-3.0\",\"beatsBaseline\":true}";

    run.markSucceeded(series, diagnosticsJson, "timesfm-3.0", 42L);

    assertThat(run.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    assertThat(run.diagnostics()).isEqualTo(diagnosticsJson);
    assertThat(run.modelVersion()).isEqualTo("timesfm-3.0");
    assertThat(run.elapsedMs()).isEqualTo(42L);
    assertThat(run.series()).containsExactlyElementsOf(series);
    ForecastDay day = run.series().get(0);
    assertThat(day.food()).isEqualByComparingTo("58.0");
    assertThat(day.bev()).isEqualByComparingTo("62.0");
    assertThat(day.baseline()).isEqualByComparingTo("115.0");
    assertThat(day.bookedCovers()).isEqualByComparingTo("142");
    assertThat(day.observedBookedCovers()).isEqualByComparingTo("60");
    assertThat(day.publicHoliday()).isTrue();
    assertThat(day.publicHolidayName()).isEqualTo("Melbourne Cup");
    assertThat(day.publicHolidayEve()).isFalse();
    assertThat(day.dayAfterPublicHoliday()).isFalse();
    assertThat(day.schoolHoliday()).isFalse();
    assertThat(day.uniInSession()).isTrue();
    assertThat(day.serviceTempMaxC()).isEqualByComparingTo("22.5");
    assertThat(day.serviceRainMm()).isEqualByComparingTo("0.0");
    assertThat(day.weatherSource()).isEqualTo("open-meteo");
  }

  @Test
  void markFailedStoresErrorMessage() {
    ForecastRun run = run();

    run.markFailed("boom");

    assertThat(run.status()).isEqualTo(ForecastRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("boom");
  }

  @Test
  void forecastDayRoundTripsDateAndQuantiles() {
    LocalDate date = LocalDate.of(2026, 10, 12);
    Map<String, BigDecimal> quantiles =
        Map.of("0.1", new BigDecimal("1.1"), "0.9", new BigDecimal("9.9"));

    ForecastDay day = new ForecastDay(date, quantiles);

    assertThat(day.date()).isEqualTo(date);
    assertThat(day.quantiles()).isEqualTo(quantiles);
  }

  private static ForecastRun run() {
    return ForecastRun.create(
        "sales.gross",
        List.of("reservations.covers"),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 10, 11),
        30,
        CREATED_BY,
        NOW);
  }
}
