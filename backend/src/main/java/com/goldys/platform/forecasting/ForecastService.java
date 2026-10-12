package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a forecast run end to end: validates the request, fetches the target and covariate
 * series through the semantic layer, assembles the model payload, calls the TimesFM inference
 * service asynchronously, and persists the outcome.
 *
 * <p>The run is created in {@code QUEUED} state and dispatched to the application task executor, so
 * a slow inference call never blocks the submission. Any run left {@code RUNNING} when the process
 * starts is flipped to {@code FAILED}, so a restart can never strand a row.
 */
@Service
public class ForecastService {

  /** The minimum number of resolved target points required before a forecast is attempted. */
  static final int MIN_HISTORY_POINTS = 14;

  /** The nine deciles TimesFM 3.0 returns, passed through to the model unchanged. */
  static final List<String> QUANTILES =
      List.of("0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9");

  private static final String MODEL_VERSION = "timesfm-3.0";
  private static final String INTERRUPTED_MESSAGE = "service restarted before completion";

  private final ForecastRunRepository runs;
  private final MetricQueryService queries;
  private final MetricCatalog catalog;
  private final TimesFmClient client;
  private final TaskExecutor executor;
  private final TimesFmProperties properties;
  private final Clock clock;

  @Autowired
  public ForecastService(
      ForecastRunRepository runs,
      MetricQueryService queries,
      MetricCatalog catalog,
      TimesFmClient client,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      TimesFmProperties properties) {
    this(runs, queries, catalog, client, executor, properties, Clock.systemUTC());
  }

  /** Test-friendly constructor: an explicit executor and clock, no qualifier. */
  ForecastService(
      ForecastRunRepository runs,
      MetricQueryService queries,
      MetricCatalog catalog,
      TimesFmClient client,
      TaskExecutor executor,
      TimesFmProperties properties,
      Clock clock) {
    this.runs = runs;
    this.queries = queries;
    this.catalog = catalog;
    this.client = client;
    this.executor = executor;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Validates the request, persists a {@code QUEUED} run, and dispatches inference to the executor.
   * The caller gets the run back immediately; the run reaches a terminal state asynchronously.
   */
  public ForecastRun submit(
      String targetMetric,
      List<String> covariateMetrics,
      LocalDate historyFrom,
      int horizonDays,
      String email) {
    if (!properties.enabled()) {
      throw new IllegalStateException("forecasting is not enabled");
    }
    if (horizonDays < 1 || horizonDays > 90) {
      throw new IllegalArgumentException("horizon must be between 1 and 90 days");
    }
    MetricId target = requireForecastable(targetMetric);
    LocalDate historyTo = LocalDate.now(clock);
    if (historyFrom.isAfter(historyTo)) {
      throw new IllegalArgumentException(
          "historyFrom must not be after historyTo (" + historyTo + ")");
    }
    TimeRange range = new TimeRange(historyFrom, historyTo, Calendar.CALENDAR);
    long resolvedPoints =
        series(target, range).points().stream().filter(p -> p.value() != null).count();
    if (resolvedPoints < MIN_HISTORY_POINTS) {
      throw new IllegalArgumentException(
          "insufficient history for "
              + targetMetric
              + ": "
              + resolvedPoints
              + " resolved points (need "
              + MIN_HISTORY_POINTS
              + ")");
    }
    ForecastRun run =
        ForecastRun.create(
            targetMetric,
            covariateMetrics,
            historyFrom,
            historyTo,
            horizonDays,
            email,
            clock.instant());
    runs.save(run);
    UUID runId = run.id();
    executor.execute(() -> execute(runId));
    return run;
  }

  /** Runs inference for a queued run and advances it to {@code SUCCEEDED} or {@code FAILED}. */
  public void execute(UUID runId) {
    ForecastRun run =
        runs.findById(runId)
            .orElseThrow(() -> new IllegalStateException("unknown forecast run: " + runId));
    run.markRunning();
    runs.save(run);

    long startNanos = System.nanoTime();
    try {
      MetricId target = MetricId.fromValue(run.targetMetric());
      TimeRange range = new TimeRange(run.historyFrom(), run.historyTo(), Calendar.CALENDAR);
      MetricSeries targetSeries = series(target, range);
      List<CovariateSeries> covariates = new ArrayList<>(run.covariates().size());
      for (String covariate : run.covariates()) {
        MetricSeries covariateSeries = series(MetricId.fromValue(covariate), range);
        covariates.add(new CovariateSeries(covariate, covariateSeries.points()));
      }
      List<LocalDate> axis = ForecastSeriesAssembler.axis(run.historyFrom(), run.historyTo());
      TimesFmRequest request =
          ForecastSeriesAssembler.request(
              axis, targetSeries.points(), covariates, run.horizonDays(), QUANTILES);
      TimesFmForecast forecast = client.forecast(request);
      run.markSucceeded(transpose(forecast), MODEL_VERSION, elapsedMs(startNanos));
    } catch (RuntimeException e) {
      run.markFailed(failureMessage(e));
    }
    runs.save(run);
  }

  /** Fetches one metric as a single daily series, rejecting ranked-list or multi-series results. */
  MetricSeries series(MetricId id, TimeRange range) {
    MetricResult result = queries.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    if (!(result instanceof TimeSeriesResult timeSeries)) {
      throw new IllegalStateException("metric " + id.value() + " did not return a time series");
    }
    if (timeSeries.series().size() != 1) {
      throw new IllegalStateException(
          "metric "
              + id.value()
              + " returned "
              + timeSeries.series().size()
              + " series; expected 1");
    }
    return timeSeries.series().get(0);
  }

  /** Flips any run stranded in {@code RUNNING} by a previous shutdown to {@code FAILED}. */
  @PostConstruct
  void failInterruptedRuns() {
    for (ForecastRun run : runs.findByStatus(ForecastRunStatus.RUNNING)) {
      run.markFailed(INTERRUPTED_MESSAGE);
      runs.save(run);
    }
  }

  private MetricId requireForecastable(String metric) {
    MetricId id = MetricId.fromValue(metric); // throws on an unknown id
    if (id == MetricId.PRODUCT_TOP_SELLERS) {
      throw new IllegalArgumentException("metric is not forecastable: " + metric);
    }
    // Defence in depth: the catalogue is the source of truth for what a metric is, so a catalogued
    // id that is missing from the catalogue (drift between enum and catalogue) is rejected too.
    catalog.definition(id);
    return id;
  }

  private List<ForecastDay> transpose(TimesFmForecast forecast) {
    List<LocalDate> dates = forecast.dates();
    Map<String, List<BigDecimal>> quantiles = forecast.quantiles();
    List<ForecastDay> days = new ArrayList<>(dates.size());
    for (int i = 0; i < dates.size(); i++) {
      Map<String, BigDecimal> byKey = new LinkedHashMap<>();
      for (Map.Entry<String, List<BigDecimal>> entry : quantiles.entrySet()) {
        byKey.put(entry.getKey(), entry.getValue().get(i));
      }
      days.add(new ForecastDay(dates.get(i), byKey));
    }
    return days;
  }

  private String failureMessage(RuntimeException e) {
    // TimesFmException messages are already operator-facing (no payload, credentials, or tokens).
    // Anything else gets a class-only label: its message may carry a URL, token, or payload.
    if (e instanceof TimesFmException) {
      return e.getMessage();
    }
    return "unexpected error: " + e.getClass().getSimpleName();
  }

  private long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
