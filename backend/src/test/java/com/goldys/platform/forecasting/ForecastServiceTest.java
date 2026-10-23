package com.goldys.platform.forecasting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class ForecastServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
  private static final LocalDate SEP_01 = LocalDate.of(2026, 9, 1);
  private static final String EMAIL = "owner@example.com";

  private final Map<UUID, ForecastRun> store = new HashMap<>();
  private final ForecastRunRepository runs = mock(ForecastRunRepository.class);
  private final MetricQueryService queries = mock(MetricQueryService.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final ForecastModelClient client = mock(ForecastModelClient.class);
  // A no-op executor: submit persists QUEUED and returns before the async execute runs, so the
  // QUEUED state is observable. The execute tests call execute(...) directly, synchronously.
  private final TaskExecutor executor = task -> {};
  private final ForecastService service =
      new ForecastService(
          runs,
          queries,
          catalog,
          client,
          executor,
          new TimesFmProperties(true, "http://localhost:8001"),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    store.clear();
    when(runs.save(any()))
        .thenAnswer(
            inv -> {
              ForecastRun run = inv.getArgument(0);
              store.put(run.id(), run);
              return run;
            });
    when(runs.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    when(runs.findByStatus(any()))
        .thenAnswer(
            inv -> store.values().stream().filter(r -> r.status() == inv.getArgument(0)).toList());
  }

  @Test
  void submitPersistsQueuedWithHistoryToToday() {
    when(queries.query(any())).thenReturn(timeSeries(MetricId.SALES_NET, 20, SEP_01));

    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, EMAIL);

    assertThat(run.status()).isEqualTo(ForecastRunStatus.QUEUED);
    assertThat(run.historyTo()).isEqualTo(TODAY);
    assertThat(run.historyFrom()).isEqualTo(SEP_01);
    assertThat(run.horizonDays()).isEqualTo(7);
    assertThat(run.targetMetric()).isEqualTo("sales.net");
    assertThat(run.createdBy()).isEqualTo(EMAIL);
    assertThat(store).containsKey(run.id());
  }

  @Test
  void horizonValidated() {
    assertThatThrownBy(() -> service.submit("sales.net", List.of(), SEP_01, 0, EMAIL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.submit("sales.net", List.of(), SEP_01, 91, EMAIL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void submitRejectsNullTargetAndHistoryFrom() {
    assertThatThrownBy(() -> service.submit(null, List.of(), SEP_01, 7, EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetMetric");
    assertThatThrownBy(() -> service.submit("sales.net", List.of(), null, 7, EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("historyFrom");
  }

  @Test
  void submitRejectsInsufficientHistory() {
    when(queries.query(any())).thenReturn(timeSeries(MetricId.SALES_NET, 3, SEP_01));

    assertThatThrownBy(() -> service.submit("sales.net", List.of(), SEP_01, 7, EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("insufficient history");
  }

  @Test
  void submitRejectsRankedList() {
    assertThatThrownBy(() -> service.submit("product.top_sellers", List.of(), SEP_01, 7, EMAIL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void submitRejectsHistoryAfterToday() {
    assertThatThrownBy(() -> service.submit("sales.net", List.of(), TODAY.plusDays(1), 7, EMAIL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void executeMarksSucceeded() {
    when(queries.query(any())).thenReturn(timeSeries(MetricId.SALES_NET, 20, SEP_01));
    when(client.forecast(any()))
        .thenReturn(
            new ForecastModelResponse(
                List.of(
                    new ForecastModelResponse.Day(
                        TODAY.plusDays(1),
                        new BigDecimal("10.5"),
                        new BigDecimal("10.1"),
                        new BigDecimal("10.9"),
                        new BigDecimal("5.0"),
                        new BigDecimal("5.5"),
                        new BigDecimal("10.0"),
                        new BigDecimal("50"),
                        1,
                        "Grand Final Friday",
                        1,
                        0,
                        0,
                        1,
                        new BigDecimal("21.4"),
                        new BigDecimal("0.0"),
                        "open-meteo"),
                    new ForecastModelResponse.Day(
                        TODAY.plusDays(2),
                        new BigDecimal("11.5"),
                        new BigDecimal("11.1"),
                        new BigDecimal("11.9"),
                        null,
                        null,
                        null,
                        new BigDecimal("60"),
                        0,
                        null,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null)),
                "{\"modelVersion\":\"timesfm-3.0\"}",
                List.of()));

    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, EMAIL);
    service.execute(run.id());

    assertThat(run.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    assertThat(run.modelVersion()).isEqualTo("timesfm-3.0");
    assertThat(run.diagnostics()).isEqualTo("{\"modelVersion\":\"timesfm-3.0\"}");
    assertThat(run.series()).hasSize(2);

    ForecastDay first = run.series().get(0);
    assertThat(first.date()).isEqualTo(TODAY.plusDays(1));
    assertThat(first.quantiles())
        .containsEntry("0.1", new BigDecimal("10.1"))
        .containsEntry("0.5", new BigDecimal("10.5"))
        .containsEntry("0.9", new BigDecimal("10.9"));
    assertThat(first.food()).isEqualByComparingTo("5.0");
    assertThat(first.bev()).isEqualByComparingTo("5.5");
    assertThat(first.baseline()).isEqualByComparingTo("10.0");
    assertThat(first.bookedCovers()).isEqualByComparingTo("50");
    assertThat(first.publicHoliday()).isTrue();
    assertThat(first.publicHolidayName()).isEqualTo("Grand Final Friday");
    assertThat(first.publicHolidayEve()).isTrue();
    assertThat(first.dayAfterPublicHoliday()).isFalse();
    assertThat(first.schoolHoliday()).isFalse();
    assertThat(first.uniInSession()).isTrue();
    assertThat(first.serviceTempMaxC()).isEqualByComparingTo("21.4");
    assertThat(first.weatherSource()).isEqualTo("open-meteo");

    ForecastDay second = run.series().get(1);
    assertThat(second.publicHoliday()).isFalse();
    assertThat(second.publicHolidayName()).isNull();
    assertThat(second.food()).isNull();
    assertThat(second.bookedCovers()).isEqualByComparingTo("60");

    ArgumentCaptor<ForecastModelRequest> request =
        ArgumentCaptor.forClass(ForecastModelRequest.class);
    verify(client).forecast(request.capture());
    assertThat(request.getValue().quantiles()).containsExactly("0.1", "0.5", "0.9");
    assertThat(request.getValue().horizon()).isEqualTo(7);
    assertThat(request.getValue().dates())
        .hasSameSizeAs(request.getValue().net())
        .hasSameSizeAs(request.getValue().bookedCovers());
  }

  @Test
  void executeMarksFailedOnClientError() {
    when(queries.query(any())).thenReturn(timeSeries(MetricId.SALES_NET, 20, SEP_01));
    when(client.forecast(any()))
        .thenThrow(new ForecastModelException("forecasting service returned HTTP 500"));

    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, EMAIL);
    service.execute(run.id());

    assertThat(run.status()).isEqualTo(ForecastRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("forecasting service returned HTTP 500");
    assertThat(run.series()).isEmpty();
  }

  @Test
  void failInterruptedRunsMarksRunningFailed() {
    ForecastRun run =
        ForecastRun.create(
            "sales.net", List.of(), SEP_01, LocalDate.of(2026, 10, 11), 7, EMAIL, NOW);
    run.markRunning();
    store.put(run.id(), run);

    service.failInterruptedRuns();

    assertThat(run.status()).isEqualTo(ForecastRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("service restarted before completion");
  }

  private static TimeSeriesResult timeSeries(MetricId id, int nonNullPoints, LocalDate from) {
    List<MetricPoint> points = new ArrayList<>();
    for (int i = 0; i < nonNullPoints; i++) {
      points.add(new MetricPoint(from.plusDays(i), new BigDecimal(100 + i)));
    }
    return new TimeSeriesResult(
        id,
        List.of(new MetricSeries(null, points)),
        List.of(),
        new MetricProvenance(
            id,
            "1",
            new TimeRange(from, from.plusDays(nonNullPoints - 1L), Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            NOW,
            List.of(),
            "1"));
  }

  @Test
  void executeUsesOriginalCheckedSnapshotEvenAfterResolvedInputsChange() {
    when(queries.query(any())).thenReturn(timeSeries(MetricId.SALES_NET, 20, SEP_01));
    when(client.forecast(any())).thenReturn(new ForecastModelResponse(List.of(), null, List.of()));
    ForecastRun run = service.submit("sales.net", List.of(), SEP_01, 7, EMAIL);
    ForecastModelRequest original = run.inputSnapshot().request();
    org.mockito.Mockito.clearInvocations(queries);
    when(queries.query(any()))
        .thenThrow(new IllegalStateException("new inputs must not be queried"));
    service.execute(run.id());
    assertThat(run.status()).isEqualTo(ForecastRunStatus.SUCCEEDED);
    verify(client).forecast(original);
    org.mockito.Mockito.verifyNoInteractions(queries);
    assertThat(run.inputSnapshot().capturedAt()).isEqualTo(NOW);
  }

  @Test
  void legacyQueuedRunDoesNotInventOriginalInputs() {
    ForecastRun run = ForecastRun.create("sales.net", List.of(), SEP_01, TODAY, 7, EMAIL, NOW);
    runs.save(run);
    service.execute(run.id());
    assertThat(run.status()).isEqualTo(ForecastRunStatus.FAILED);
    assertThat(run.errorMessage()).contains("Original input snapshot unavailable");
    org.mockito.Mockito.verifyNoInteractions(client);
  }
}
