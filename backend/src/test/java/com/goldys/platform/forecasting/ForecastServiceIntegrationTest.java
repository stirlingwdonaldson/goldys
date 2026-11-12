package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "forecasting.timesfm.enabled=true")
@Import({PostgresContainerConfiguration.class, ForecastServiceIntegrationTest.SyncExecutor.class})
class ForecastServiceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
  private static final LocalDate SEP_01 = LocalDate.of(2026, 9, 1);
  private static final LocalDate OCT_11 = LocalDate.of(2026, 10, 11);
  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

  @Autowired JdbcTemplate jdbc;
  @Autowired ForecastService service;
  @Autowired ForecastRunRepository repository;
  @Autowired com.goldys.platform.application.ForecastingApplicationService application;

  @MockitoBean ForecastModelClient client;
  @MockitoBean MetricQueryService queries;

  @BeforeEach
  void setUp() {
    jdbc.update("truncate table forecast_run");
  }

  @Test
  void submitRunsSynchronouslyToSucceededAndPersistsSeries() {
    when(queries.query(any())).thenReturn(targetSeries());
    when(client.forecast(any()))
        .thenReturn(
            new ForecastModelResponse(
                List.of(
                    new ForecastModelResponse.Day(
                        OCT_12,
                        new BigDecimal("10.5"),
                        new BigDecimal("10.1"),
                        new BigDecimal("10.9"),
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null),
                    new ForecastModelResponse.Day(
                        OCT_12.plusDays(1),
                        new BigDecimal("11.5"),
                        new BigDecimal("11.1"),
                        new BigDecimal("11.9"),
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null)),
                null,
                List.of("XGBoost diagnostics unavailable; headline forecast retained"),
                "runtime-reported-model"));

    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, "owner@example.com");

    ForecastRun reloaded = repository.findById(run.id()).orElseThrow();
    assertThat(reloaded.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    assertThat(reloaded.modelVersion()).isEqualTo("runtime-reported-model");
    assertThat(reloaded.series()).hasSize(2);
    assertThat(reloaded.series().get(0).date()).isEqualTo(OCT_12);
    assertThat(reloaded.series().get(0).quantiles())
        .containsEntry("0.5", new BigDecimal("10.5"))
        .containsEntry("0.9", new BigDecimal("10.9"));
    assertThat(reloaded.errorMessage()).isNull();
    assertThat(reloaded.diagnostics()).isNull();
    assertThat(reloaded.runtimeWarnings())
        .containsExactly("XGBoost diagnostics unavailable; headline forecast retained");
    var owner =
        new com.goldys.platform.auth.UserRole(
            new com.goldys.platform.auth.DepartmentCode("ALL"),
            new com.goldys.platform.auth.SeniorityCode("OWNER"));
    var dto = application.get(owner, run.id());
    assertThat(dto.diagnostics()).isNull();
    assertThat(dto.modelVersion()).isEqualTo("runtime-reported-model");
    assertThat(dto.notices())
        .contains(
            "XGBoost diagnostics unavailable; headline forecast retained",
            "Forecast uses an immutable input snapshot; incomplete inputs remain gaps, not zero");
  }

  @Test
  void recoveryPersistsBothInterruptedStatesAndDoesNotTouchCompletedRuns() {
    ForecastRun queued =
        ForecastRun.create("sales.net", List.of(), SEP_01, OCT_11, 7, "owner@example.com", NOW);
    ForecastRun running =
        ForecastRun.create("sales.net", List.of(), SEP_01, OCT_11, 7, "owner@example.com", NOW);
    running.markRunning();
    ForecastRun finished =
        ForecastRun.create("sales.net", List.of(), SEP_01, OCT_11, 7, "owner@example.com", NOW);
    finished.markSucceeded(List.of(), "model", 1);
    repository.saveAll(List.of(queued, running, finished));
    service.failInterruptedRuns();
    assertThat(repository.findByStatus(ForecastRunStatus.QUEUED)).isEmpty();
    assertThat(repository.findByStatus(ForecastRunStatus.RUNNING)).isEmpty();
    assertThat(repository.findByStatus(ForecastRunStatus.FAILED)).hasSize(2);
    assertThat(repository.findById(finished.id()).orElseThrow().status())
        .isEqualTo(ForecastRunStatus.SUCCEEDED);
  }

  @Test
  void rejectedDispatchFailureSurvivesReload() {
    when(queries.query(any())).thenReturn(targetSeries());
    ForecastService rejecting =
        new ForecastService(
            repository,
            queries,
            new com.goldys.platform.semantic.catalog.MetricCatalog(),
            client,
            task -> {
              throw new java.util.concurrent.RejectedExecutionException();
            },
            new TimesFmProperties(true, "http://localhost:8001"),
            java.time.Clock.systemUTC());
    ForecastRun run = rejecting.submit("sales.net", List.of(), SEP_01, 7, "owner@example.com");
    assertThat(repository.findById(run.id()).orElseThrow().status())
        .isEqualTo(ForecastRunStatus.FAILED);
  }

  @Test
  void malformedRuntimeWarningsBecomePersistedFailureInsteadOfStrandingRunning() {
    when(queries.query(any())).thenReturn(targetSeries());
    when(client.forecast(any()))
        .thenReturn(
            new ForecastModelResponse(
                List.of(), null, java.util.Collections.singletonList(null), "runtime-model"));
    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, "owner@example.com");
    ForecastRun loaded = repository.findById(run.id()).orElseThrow();
    assertThat(loaded.status()).isEqualTo(ForecastRunStatus.FAILED);
    assertThat(loaded.errorMessage()).isEqualTo("unexpected error: NullPointerException");
    assertThat(loaded.series()).isEmpty();
  }

  private static TimeSeriesResult targetSeries() {
    List<MetricPoint> points = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      points.add(new MetricPoint(SEP_01.plusDays(i), new BigDecimal(100 + i)));
    }
    return new TimeSeriesResult(
        MetricId.SALES_GROSS,
        List.of(new MetricSeries(null, points)),
        List.of(),
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(SEP_01, OCT_11, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            NOW,
            List.of(),
            "1"));
  }

  /** Replaces the auto-configured async executor so {@code submit} runs {@code execute} inline. */
  @TestConfiguration(proxyBeanMethods = false)
  static class SyncExecutor {
    @Bean(name = "applicationTaskExecutor")
    TaskExecutor applicationTaskExecutor() {
      return new SyncTaskExecutor();
    }
  }
}
