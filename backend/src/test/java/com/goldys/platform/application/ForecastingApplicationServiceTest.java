package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
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
        .hasSize(1);

    ForecastMetricDto sales =
        metrics.stream().filter(m -> m.metric().equals("sales.net")).findFirst().orElseThrow();
    assertThat(sales.name()).isEqualTo("Net sales");
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
    verify(permissions, atLeastOnce())
        .require(role(), new ResourceKey("reconciliation.sales"), PermissionAction.READ);
    verify(permissions, atLeastOnce())
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
  void forecastPurposeIncludesAggregateSupportingData() {
    ForecastRun run = queuedRun("sales.net", List.of());
    when(forecasts.submit("sales.net", List.of(), SEP_01, 7, EMAIL)).thenReturn(run);
    service().create(role(), EMAIL, new CreateForecastRequest("sales.net", List.of(), SEP_01, 7));
    verify(permissions).require(role(), new ResourceKey("forecasting"), PermissionAction.READ);
    verify(permissions, never())
        .require(role(), new ResourceKey("reservations.metrics"), PermissionAction.READ);
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

  @Test
  void getReturnsDiagnosticsAndSplit() {
    ForecastRun run = succeededRunWithSplit();
    when(runs.findById(run.id())).thenReturn(Optional.of(run));

    ForecastRunDto dto = service().get(role(), run.id());

    assertThat(dto.diagnostics()).isNotNull();
    ForecastDiagnosticsDto diagnostics = dto.diagnostics();
    assertThat(diagnostics.modelVersion()).isEqualTo("timesfm-3.0");
    assertThat(diagnostics.trainedRows()).isEqualTo(365);
    assertThat(diagnostics.reliable()).isTrue();
    assertThat(diagnostics.warnings()).isEmpty();
    assertThat(diagnostics.features()).containsExactly("bookedCovers", "dow");
    assertThat(diagnostics.shareFeatures()).containsExactly("bookedCovers");
    assertThat(diagnostics.holdoutNetXgboost().mae()).isEqualByComparingTo("42.5");
    assertThat(diagnostics.holdoutNetXgboost().rmse()).isEqualByComparingTo("60.0");
    assertThat(diagnostics.holdoutNetXgboost().mape()).isEqualByComparingTo("0.13");
    assertThat(diagnostics.holdoutNetXgboost().n()).isEqualTo(120);
    assertThat(diagnostics.holdoutNetBaseline().mae()).isEqualByComparingTo("50.0");
    assertThat(diagnostics.holdoutFood()).isNull();
    assertThat(diagnostics.cvNetBaseline()).isNull();
    assertThat(diagnostics.beatsBaseline()).isTrue();
    assertThat(diagnostics.importance()).hasSize(2);
    assertThat(diagnostics.importance().get(0).feature()).isEqualTo("bookedCovers");
    assertThat(diagnostics.importance().get(0).importance()).isEqualByComparingTo("0.62");

    ForecastDiagnosticsDto.Backtest backtest = diagnostics.backtest();
    assertThat(backtest).isNotNull();
    assertThat(backtest.rows()).hasSize(2);
    assertThat(backtest.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(backtest.rows().get(0).day()).isEqualTo("Monday");
    assertThat(backtest.rows().get(0).actualNet()).isEqualByComparingTo("4100.0");
    assertThat(backtest.rows().get(0).predictedNet()).isEqualByComparingTo("4050.0");
    assertThat(backtest.rows().get(0).absPctError()).isEqualByComparingTo("0.012");
    assertThat(backtest.rows().get(0).actualFood()).isEqualByComparingTo("1800.0");
    assertThat(backtest.byWeekday()).hasSize(1);
    assertThat(backtest.byWeekday().get(0).day()).isEqualTo("Monday");
    assertThat(backtest.byWeekday().get(0).n()).isEqualTo(2);
    assertThat(backtest.byWeekday().get(0).mae()).isEqualByComparingTo("47.5");
    assertThat(backtest.residualMean()).isEqualByComparingTo("3.2");
    assertThat(backtest.residualStd()).isEqualByComparingTo("12.1");
    assertThat(backtest.worstDays()).hasSize(1);

    assertThat(dto.series()).hasSize(1);
    ForecastSeriesPointDto point = dto.series().get(0);
    assertThat(point.median()).isEqualByComparingTo("4200.5");
    assertThat(point.p10()).isEqualByComparingTo("3900.0");
    assertThat(point.p90()).isEqualByComparingTo("4600.0");
    assertThat(point.food()).isEqualByComparingTo("1900.0");
    assertThat(point.bev()).isEqualByComparingTo("2300.5");
    assertThat(point.baseline()).isEqualByComparingTo("4100.0");
    assertThat(point.bookedCovers()).isEqualByComparingTo("85.0");
    assertThat(point.publicHoliday()).isFalse();
    assertThat(point.publicHolidayName()).isNull();
    assertThat(point.publicHolidayEve()).isFalse();
    assertThat(point.dayAfterPublicHoliday()).isTrue();
    assertThat(point.schoolHoliday()).isFalse();
    assertThat(point.uniInSession()).isTrue();
    assertThat(point.serviceTempMaxC()).isEqualByComparingTo("21.4");
    assertThat(point.serviceRainMm()).isEqualByComparingTo("0.0");
    assertThat(point.weatherSource()).isEqualTo("open-meteo");
  }

  @Test
  void getMapsNullDiagnosticsToNull() {
    // succeededRun() marks the run with the 3-arg markSucceeded, which leaves diagnostics null.
    ForecastRun run = succeededRun();
    when(runs.findById(run.id())).thenReturn(Optional.of(run));

    ForecastRunDto dto = service().get(role(), run.id());

    assertThat(dto.diagnostics()).isNull();
    assertThat(dto.series()).isNotNull();
  }

  @Test
  void getMapsUnparseableDiagnosticsToNull() {
    ForecastRun run = succeededRun();
    run.markSucceeded(run.series(), "not-json", "timesfm-3.0", 10L);
    when(runs.findById(run.id())).thenReturn(Optional.of(run));

    ForecastRunDto dto = service().get(role(), run.id());

    assertThat(dto.diagnostics()).isNull();
    assertThat(dto.series()).isNotNull();
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

  private static ForecastRun succeededRunWithSplit() {
    ForecastRun run = queuedRun("sales.gross", List.of());
    run.markSucceeded(
        List.of(
            new ForecastDay(
                LocalDate.of(2026, 10, 16),
                Map.of(
                    "0.5", new BigDecimal("4200.5"),
                    "0.1", new BigDecimal("3900.0"),
                    "0.9", new BigDecimal("4600.0")),
                new BigDecimal("1900.0"),
                new BigDecimal("2300.5"),
                new BigDecimal("4100.0"),
                new BigDecimal("85.0"),
                false,
                null,
                false,
                true,
                false,
                true,
                new BigDecimal("21.4"),
                new BigDecimal("0.0"),
                "open-meteo")),
        DIAGNOSTICS_JSON,
        "timesfm-3.0",
        10L);
    return run;
  }

  private static final String DIAGNOSTICS_JSON =
      "{\"modelVersion\":\"timesfm-3.0\",\"trainedRows\":365,\"reliable\":true,"
          + "\"warnings\":[],\"features\":[\"bookedCovers\",\"dow\"],"
          + "\"shareFeatures\":[\"bookedCovers\"],"
          + "\"holdoutNetXgboost\":{\"mae\":42.5,\"rmse\":60.0,\"mape\":0.13,\"n\":120},"
          + "\"holdoutNetBaseline\":{\"mae\":50.0,\"rmse\":68.0,\"mape\":0.15,\"n\":120},"
          + "\"holdoutFood\":null,\"holdoutBev\":null,"
          + "\"cvNetXgboost\":null,\"cvNetBaseline\":null,\"beatsBaseline\":true,"
          + "\"importance\":[{\"feature\":\"bookedCovers\",\"importance\":0.62},"
          + "{\"feature\":\"dow\",\"importance\":0.21}],"
          + "\"backtest\":{\"rows\":["
          + "{\"date\":\"2026-10-05\",\"day\":\"Monday\",\"actualNet\":4100.0,"
          + "\"predictedNet\":4050.0,\"error\":-50.0,\"absPctError\":0.012,"
          + "\"actualFood\":1800.0,\"predictedFood\":1780.0,\"actualBev\":2300.0,"
          + "\"predictedBev\":2270.0,\"baselineNet\":4000.0},"
          + "{\"date\":\"2026-10-06\",\"day\":\"Tuesday\",\"actualNet\":3900.0,"
          + "\"predictedNet\":3950.0,\"error\":50.0,\"absPctError\":0.013,"
          + "\"actualFood\":1700.0,\"predictedFood\":1720.0,\"actualBev\":2200.0,"
          + "\"predictedBev\":2230.0,\"baselineNet\":3850.0}],"
          + "\"byWeekday\":[{\"day\":\"Monday\",\"n\":2,\"mae\":47.5,\"mape\":0.013,"
          + "\"meanActual\":4050.0,\"meanPredicted\":4000.0}],"
          + "\"residualMean\":3.2,\"residualStd\":12.1,"
          + "\"worstDays\":[{\"date\":\"2026-10-05\",\"day\":\"Monday\",\"actualNet\":4100.0,"
          + "\"predictedNet\":4050.0,\"error\":-50.0,\"absPctError\":0.012,"
          + "\"actualFood\":1800.0,\"predictedFood\":1780.0,\"actualBev\":2300.0,"
          + "\"predictedBev\":2270.0,\"baselineNet\":4000.0}]}}";

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

  @Test
  void listRechecksLegacyTargetAndCovariatePermissionsBeforeReturningPayload() {
    ForecastRun run = queuedRun("labour.cost", List.of());
    when(runs.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(run));
    doThrow(AccessDeniedException.forResource("labour.cost"))
        .when(permissions)
        .require(role(), new ResourceKey("labour.cost"), PermissionAction.READ);
    assertThatThrownBy(() -> service().list(role())).isInstanceOf(AccessDeniedException.class);
  }
}
