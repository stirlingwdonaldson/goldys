package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ForecastRunRepositoryIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:15:30Z");
  private static final String CREATED_BY = "owner@example.com";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired JdbcTemplate jdbc;
  @Autowired ForecastRunRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table forecast_run");
  }

  @Test
  void savesAndReloadsRunWithJsonbSeriesAndCovariates() {
    List<ForecastDay> series =
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 12),
                Map.of(
                    "0.1", new BigDecimal("100.5"),
                    "0.5", new BigDecimal("120.0"),
                    "0.9", new BigDecimal("140.5"))));
    ForecastRun run =
        ForecastRun.create(
            "sales.gross",
            List.of("reservations.covers", "labour.cost"),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 10, 11),
            30,
            CREATED_BY,
            NOW);
    run.markSucceeded(series, "timesfm-3.0", 1L);

    repository.save(run);
    ForecastRun reloaded = repository.findById(run.id()).orElseThrow();

    assertThat(reloaded.id()).isEqualTo(run.id());
    assertThat(reloaded.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    assertThat(reloaded.targetMetric()).isEqualTo("sales.gross");
    assertThat(reloaded.covariates()).containsExactly("reservations.covers", "labour.cost");
    assertThat(reloaded.historyFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(reloaded.historyTo()).isEqualTo(LocalDate.of(2026, 10, 11));
    assertThat(reloaded.horizonDays()).isEqualTo(30);
    assertThat(reloaded.createdBy()).isEqualTo(CREATED_BY);
    assertThat(reloaded.createdAt()).isEqualTo(NOW);
    assertThat(reloaded.series()).containsExactlyElementsOf(series);
    assertThat(reloaded.modelVersion()).isEqualTo("timesfm-3.0");
    assertThat(reloaded.elapsedMs()).isEqualTo(1L);
  }

  @Test
  void savesAndReloadsRunWithDiagnosticsAndEnrichedSeries() throws Exception {
    String diagnosticsJson = "{\"modelVersion\":\"timesfm-3.0\",\"beatsBaseline\":true}";
    List<ForecastDay> series =
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 12),
                Map.of("0.5", new BigDecimal("120.0")),
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
                "open-meteo"));
    ForecastRun run =
        ForecastRun.create(
            "sales.gross",
            List.of(),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 10, 11),
            30,
            CREATED_BY,
            NOW);
    run.markSucceeded(series, diagnosticsJson, "timesfm-3.0", 2L);

    repository.save(run);
    ForecastRun reloaded = repository.findById(run.id()).orElseThrow();

    assertThat((JsonNode) MAPPER.readTree(reloaded.diagnostics()))
        .isEqualTo(MAPPER.readTree(diagnosticsJson));
    assertThat(reloaded.series()).containsExactlyElementsOf(series);
    ForecastDay day = reloaded.series().get(0);
    assertThat(day.food()).isEqualByComparingTo("58.0");
    assertThat(day.bev()).isEqualByComparingTo("62.0");
    assertThat(day.baseline()).isEqualByComparingTo("115.0");
    assertThat(day.bookedCovers()).isEqualByComparingTo("142");
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
  void findAllByOrderByCreatedAtDescOrdersNewestFirst() {
    ForecastRun oldest = run("sales.gross", NOW);
    ForecastRun middle = run("labour.cost", NOW.plusSeconds(1));
    ForecastRun newest = run("reservations.covers", NOW.plusSeconds(2));

    repository.save(middle);
    repository.save(newest);
    repository.save(oldest);

    assertThat(repository.findAllByOrderByCreatedAtDesc())
        .extracting(ForecastRun::id)
        .containsExactly(newest.id(), middle.id(), oldest.id());
  }

  @Test
  void findByStatusFindsOnlyMatchingRows() {
    ForecastRun queued = run("sales.gross", NOW);
    ForecastRun running = run("labour.cost", NOW.plusSeconds(1));
    running.markRunning();

    repository.save(queued);
    repository.save(running);

    assertThat(repository.findByStatus(ForecastRunStatus.RUNNING))
        .extracting(ForecastRun::id)
        .containsExactly(running.id());
    assertThat(repository.findByStatus(ForecastRunStatus.QUEUED))
        .extracting(ForecastRun::id)
        .containsExactly(queued.id());
    assertThat(repository.findByStatus(ForecastRunStatus.SUCCEEDED)).isEmpty();
  }

  private static ForecastRun run(String targetMetric, Instant createdAt) {
    return ForecastRun.create(
        targetMetric,
        List.of(),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 10, 11),
        30,
        CREATED_BY,
        createdAt);
  }

  @Test
  void exactInputSnapshotRoundTripsWithNullGapsAndQualifications() {
    var date = LocalDate.of(2026, 10, 11);
    var request =
        new ForecastModelRequest(
            List.of(date, date.plusDays(1)),
            java.util.Arrays.asList(new BigDecimal("100"), null),
            null,
            null,
            null,
            null,
            1,
            List.of("0.5"));
    ForecastRun run = ForecastRun.create("sales.net", List.of(), date, date, 1, CREATED_BY, NOW);
    run.captureInput(
        new ForecastInputSnapshot(request, Map.of(), List.of("Incomplete: one missing day"), NOW));
    repository.save(run);
    var loaded = repository.findById(run.id()).orElseThrow();
    assertThat(loaded.inputSnapshot().request().net()).containsExactly(new BigDecimal("100"), null);
    assertThat(loaded.inputSnapshot().notices()).containsExactly("Incomplete: one missing day");
    assertThat(loaded.inputSnapshot().capturedAt()).isEqualTo(NOW);
  }
}
