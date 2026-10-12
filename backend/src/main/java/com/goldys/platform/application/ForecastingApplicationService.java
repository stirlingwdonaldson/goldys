package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.forecasting.ForecastDay;
import com.goldys.platform.forecasting.ForecastRun;
import com.goldys.platform.forecasting.ForecastRunRepository;
import com.goldys.platform.forecasting.ForecastRunStatus;
import com.goldys.platform.forecasting.ForecastService;
import com.goldys.platform.forecasting.ForecastableMetrics;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricDefinition;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The public forecasting use-case boundary: authorization, DTO mapping, and compare-to-actuals.
 *
 * <p>Every entry point is gated on the {@code forecasting} resource READ, and the metrics that feed
 * a forecast are additionally gated on their own catalogue {@code requiredPermission} — a denied
 * create throws rather than silently dropping the offending metric. {@link #metrics} deliberately
 * returns the whole forecastable list without a per-metric filter for the same reason.
 */
@Service
public class ForecastingApplicationService {

  static final ResourceKey RESOURCE = new ResourceKey("forecasting");

  private final PermissionService permissions;
  private final MetricCatalog catalog;
  private final ForecastService forecasts;
  private final MetricQueryService queries;
  private final ForecastRunRepository runs;
  private final Clock clock;

  @Autowired
  public ForecastingApplicationService(
      PermissionService permissions,
      MetricCatalog catalog,
      ForecastService forecasts,
      MetricQueryService queries,
      ForecastRunRepository runs) {
    this(permissions, catalog, forecasts, queries, runs, Clock.systemUTC());
  }

  /** Test-friendly constructor with an explicit clock for the compare-to-actuals date boundary. */
  ForecastingApplicationService(
      PermissionService permissions,
      MetricCatalog catalog,
      ForecastService forecasts,
      MetricQueryService queries,
      ForecastRunRepository runs,
      Clock clock) {
    this.permissions = permissions;
    this.catalog = catalog;
    this.forecasts = forecasts;
    this.queries = queries;
    this.runs = runs;
    this.clock = clock;
  }

  /** The forecastable catalogue metrics (metadata only), for the create-forecast picker. */
  public List<ForecastMetricDto> metrics(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return ForecastableMetrics.forecastable(catalog).stream().map(this::toMetricDto).toList();
  }

  /** Submits a forecast run after authorizing the target and each covariate's metric. */
  public ForecastRunDto create(UserRole role, String email, CreateForecastRequest request) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    requireMetricRead(role, request.target());
    for (String covariate : request.covariates()) {
      requireMetricRead(role, covariate);
    }
    ForecastRun run =
        forecasts.submit(
            request.target(),
            request.covariates(),
            request.historyFrom(),
            request.horizonDays(),
            email);
    return toRunDto(run);
  }

  /** Loads one run, re-checking the target metric's permission at read time. */
  public ForecastRunDto get(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    ForecastRun run = loadRun(id);
    requireMetricRead(role, run.targetMetric());
    return toRunDto(run);
  }

  /** All runs, newest first. */
  public List<ForecastRunDto> list(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return runs.findAllByOrderByCreatedAtDesc().stream().map(this::toRunDto).toList();
  }

  /** Forecast-versus-actual for a succeeded run, with MAPE/MAE over comparable past dates. */
  public ForecastComparisonDto comparison(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    ForecastRun run = loadRun(id);
    requireMetricRead(role, run.targetMetric());

    List<ForecastDay> days = run.series();
    if (days.isEmpty()) {
      return new ForecastComparisonDto(run.id(), null, null, List.of());
    }

    LocalDate today = LocalDate.now(clock);
    LocalDate firstForecastDate = days.get(0).date();
    MetricId target = MetricId.fromValue(run.targetMetric());
    MetricResult result =
        queries.query(
            new MetricQuery(
                target,
                new TimeRange(firstForecastDate, today, Calendar.CALENDAR),
                TimeGrain.DAY,
                Set.of(),
                null));
    Map<LocalDate, BigDecimal> actualByDate = actualsByDate(singleSeries(result));

    List<ForecastComparisonPointDto> points = new ArrayList<>(days.size());
    BigDecimal maeSum = BigDecimal.ZERO;
    int maeCount = 0;
    BigDecimal mapeSum = BigDecimal.ZERO;
    int mapeCount = 0;
    for (ForecastDay day : days) {
      BigDecimal predicted = day.quantiles().get("0.5");
      BigDecimal actual = day.date().isBefore(today) ? actualByDate.get(day.date()) : null;
      points.add(new ForecastComparisonPointDto(day.date(), predicted, actual));
      if (actual != null) {
        BigDecimal error = actual.subtract(predicted).abs();
        maeSum = maeSum.add(error);
        maeCount++;
        if (actual.signum() != 0) {
          mapeSum = mapeSum.add(error.divide(actual.abs(), MathContext.DECIMAL64));
          mapeCount++;
        }
      }
    }

    Double mae =
        maeCount == 0
            ? null
            : maeSum.divide(BigDecimal.valueOf(maeCount), MathContext.DECIMAL64).doubleValue();
    Double mape =
        mapeCount == 0
            ? null
            : mapeSum.divide(BigDecimal.valueOf(mapeCount), MathContext.DECIMAL64).doubleValue();
    return new ForecastComparisonDto(run.id(), mape, mae, points);
  }

  private ForecastMetricDto toMetricDto(MetricId id) {
    MetricDefinition definition = catalog.definition(id);
    return new ForecastMetricDto(
        id.value(),
        definition.name(),
        definition.unit(),
        definition.sourceDomain(),
        definition.requiredPermission());
  }

  private void requireMetricRead(UserRole role, String metric) {
    MetricId id = MetricId.fromValue(metric);
    String permission = catalog.definition(id).requiredPermission();
    permissions.require(role, new ResourceKey(permission), PermissionAction.READ);
  }

  private ForecastRun loadRun(UUID id) {
    return runs.findById(id)
        .orElseThrow(() -> new NoSuchElementException("unknown forecast run: " + id));
  }

  private ForecastRunDto toRunDto(ForecastRun run) {
    List<ForecastSeriesPointDto> series = null;
    if (run.status() == ForecastRunStatus.SUCCEEDED) {
      series = run.series().stream().map(this::toSeriesPoint).toList();
    }
    return new ForecastRunDto(
        run.id(),
        run.status().name(),
        run.targetMetric(),
        run.covariates(),
        run.historyFrom(),
        run.historyTo(),
        run.horizonDays(),
        run.createdAt(),
        series,
        run.errorMessage(),
        run.modelVersion());
  }

  private ForecastSeriesPointDto toSeriesPoint(ForecastDay day) {
    Map<String, BigDecimal> quantiles = day.quantiles();
    return new ForecastSeriesPointDto(
        day.date(), quantiles.get("0.5"), quantiles.get("0.1"), quantiles.get("0.9"));
  }

  private static MetricSeries singleSeries(MetricResult result) {
    if (!(result instanceof TimeSeriesResult timeSeries)) {
      throw new IllegalStateException("metric did not return a time series");
    }
    if (timeSeries.series().size() != 1) {
      throw new IllegalStateException(
          "metric returned " + timeSeries.series().size() + " series; expected 1");
    }
    return timeSeries.series().get(0);
  }

  private static Map<LocalDate, BigDecimal> actualsByDate(MetricSeries series) {
    Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
    for (MetricPoint point : series.points()) {
      byDate.putIfAbsent(point.bucketStart(), point.value());
    }
    return byDate;
  }
}
