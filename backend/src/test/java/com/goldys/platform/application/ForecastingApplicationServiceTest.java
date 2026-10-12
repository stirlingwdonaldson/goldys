package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.forecasting.ForecastDay;
import com.goldys.platform.forecasting.ForecastRun;
import com.goldys.platform.forecasting.ForecastRunRepository;
import com.goldys.platform.forecasting.ForecastService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForecastingApplicationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
  private static final LocalDate SEP_01 = LocalDate.of(2026, 9, 1);
  private static final String EMAIL = "owner@example.com";

  private final PermissionService permissions = mock(PermissionService.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final ForecastService forecasts = mock(ForecastService.class);
  private final MetricQueryService queries = mock(MetricQueryService.class);
  private final ForecastRunRepository runs = mock(ForecastRunRepository.class);

  private ForecastingApplicationService service() {
    return new ForecastingApplicationService(
        permissions, catalog, forecasts, queries, runs, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void metricsRequiresForecastingRead() {
    List<ForecastMetricDto> metrics = service().metrics(role());

    verify(permissions).require(role(), new ResourceKey("forecasting"), PermissionAction.READ);

    assertThat(metrics)
        .extracting(ForecastMetricDto::metric)
        .doesNotContain("product.top_sellers")
        .hasSize(catalog.ids().size() - 1);

    ForecastMetricDto sales =
        metrics.stream().filter(m -> m.metric().equals("sales.gross")).findFirst().orElseThrow();
    assertThat(sales.name()).isEqualTo("Gross sales");
    assertThat(sales.unit()).isEqualTo("AUD");
    assertThat(sales.sourceDomain()).isEqualTo("resolved_daily_sales");
    assertThat(sales.requiredPermission()).isEqualTo("reconciliation.sales");
  }

  @Test
  void createRequiresMetricPermission() {
    ForecastRun run = queuedRun("sales.gross", List.of("reservations.covers"));
    when(forecasts.submit("sales.gross", List.of("reservations.covers"), SEP_01, 7, EMAIL))
        .thenReturn(run);

    service()
        .create(
            role(),
            EMAIL,
            new CreateForecastRequest("sales.gross", List.of("reservations.covers"), SEP_01, 7));

    verify(permissions).require(role(), new ResourceKey("forecasting"), PermissionAction.READ);
    verify(permissions)
        .require(role(), new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    verify(permissions)
        .require(role(), new ResourceKey("reservations.metrics"), PermissionAction.READ);
    verify(forecasts).submit("sales.gross", List.of("reservations.covers"), SEP_01, 7, EMAIL);

    // A denied metric permission throws before the forecast is ever submitted.
    doThrow(AccessDeniedException.forResource("reservations.metrics"))
        .when(permissions)
        .require(role(), new ResourceKey("reservations.metrics"), PermissionAction.READ);
    assertThatThrownBy(
            () ->
                service()
                    .create(
                        role(),
                        EMAIL,
                        new CreateForecastRequest(
                            "sales.gross", List.of("reservations.covers"), SEP_01, 7)))
        .isInstanceOf(AccessDeniedException.class);
    verify(forecasts, times(1)).submit(any(), any(), any(), anyInt(), any());
  }

  @Test
  void getRequiresMetricPermission() {
    ForecastRun run = queuedRun("sales.gross", List.of());
    when(runs.findById(run.id())).thenReturn(Optional.of(run));

    ForecastRunDto dto = service().get(role(), run.id());

    verify(permissions).require(role(), new ResourceKey("forecasting"), PermissionAction.READ);
    verify(permissions)
        .require(role(), new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    assertThat(dto.id()).isEqualTo(run.id());
    assertThat(dto.status()).isEqualTo("QUEUED");
  }

  @Test
  void comparisonComputesMapeMae() {
    ForecastRun run = succeededRun();
    when(runs.findById(run.id())).thenReturn(Optional.of(run));
    when(queries.query(any())).thenReturn(actuals());

    ForecastComparisonDto comparison = service().comparison(role(), run.id());

    // Hand-computed: 10-10 (pred 100, actual 200) and 10-11 (pred 110, actual 100).
    // MAE = (100 + 10) / 2 = 55; MAPE = (0.5 + 0.1) / 2 = 0.3.
    assertThat(comparison.runId()).isEqualTo(run.id());
    assertThat(comparison.mae()).isCloseTo(55.0, offset(1e-9));
    assertThat(comparison.mape()).isCloseTo(0.3, offset(1e-9));

    assertThat(comparison.points()).hasSize(4);
    assertThat(comparison.points().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(comparison.points().get(0).predictedMedian()).isEqualByComparingTo("100");
    assertThat(comparison.points().get(0).actual()).isEqualByComparingTo("200");
    assertThat(comparison.points().get(1).actual()).isEqualByComparingTo("100");
    // Today and future dates carry no resolved value yet.
    assertThat(comparison.points().get(2).actual()).isNull();
    assertThat(comparison.points().get(3).actual()).isNull();
  }

  @Test
  void comparisonReturnsNullForFutureOnlyRun() {
    ForecastRun run = futureOnlyRun();
    when(runs.findById(run.id())).thenReturn(Optional.of(run));

    ForecastComparisonDto comparison = service().comparison(role(), run.id());

    assertThat(comparison.mape()).isNull();
    assertThat(comparison.mae()).isNull();
    assertThat(comparison.points()).hasSize(2);
    assertThat(comparison.points()).allSatisfy(p -> assertThat(p.actual()).isNull());
    // No actuals range is ever built for an all-future forecast.
    verify(queries, never()).query(any());
  }

  private static ForecastRun queuedRun(String target, List<String> covariates) {
    return ForecastRun.create(target, covariates, SEP_01, LocalDate.of(2026, 10, 9), 4, EMAIL, NOW);
  }

  private static ForecastRun succeededRun() {
    ForecastRun run = queuedRun("sales.gross", List.of());
    run.markSucceeded(
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 10),
                Map.of(
                    "0.5", new BigDecimal("100"),
                    "0.1", new BigDecimal("90"),
                    "0.9", new BigDecimal("110"))),
            new ForecastDay(LocalDate.of(2026, 10, 11), Map.of("0.5", new BigDecimal("110"))),
            new ForecastDay(LocalDate.of(2026, 10, 12), Map.of("0.5", new BigDecimal("120"))),
            new ForecastDay(LocalDate.of(2026, 10, 13), Map.of("0.5", new BigDecimal("130")))),
        "timesfm-3.0",
        10L);
    return run;
  }

  private static ForecastRun futureOnlyRun() {
    ForecastRun run = queuedRun("sales.gross", List.of());
    run.markSucceeded(
        List.of(
            new ForecastDay(LocalDate.of(2026, 10, 13), Map.of("0.5", new BigDecimal("130"))),
            new ForecastDay(LocalDate.of(2026, 10, 14), Map.of("0.5", new BigDecimal("140")))),
        "timesfm-3.0",
        10L);
    return run;
  }

  private static TimeSeriesResult actuals() {
    return new TimeSeriesResult(
        MetricId.SALES_GROSS,
        List.of(
            new MetricSeries(
                null,
                List.of(
                    new MetricPoint(LocalDate.of(2026, 10, 10), new BigDecimal("200")),
                    new MetricPoint(LocalDate.of(2026, 10, 11), new BigDecimal("100"))))),
        List.of(),
        new MetricProvenance(
            MetricId.SALES_GROSS,
            "1",
            new TimeRange(LocalDate.of(2026, 10, 10), TODAY, Calendar.CALENDAR),
            TimeGrain.DAY,
            "resolved_daily_sales",
            NOW,
            List.of(),
            "1"));
  }
}
