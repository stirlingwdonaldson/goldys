package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;

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
}
