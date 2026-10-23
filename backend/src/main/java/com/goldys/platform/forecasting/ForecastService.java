package com.goldys.platform.forecasting;

import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
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
 * Orchestrates a forecast run end to end: validates the request, fetches the target and the
 * internal covariate series through the semantic layer, assembles the model payload, calls the
 * self-hosted forecasting runtime asynchronously, and persists the outcome.
 *
 * <p>The run is created in {@code QUEUED} state and dispatched to the application task executor, so
 * a slow inference call never blocks the submission. Any run left {@code RUNNING} when the process
 * starts is flipped to {@code FAILED}, so a restart can never strand a row.
 */
@Service
public class ForecastService {

  /** The minimum number of resolved target points required before a forecast is attempted. */
  static final int MIN_HISTORY_POINTS = 14;

  /**
   * The quantile levels requested from the runtime. The wire only carries the median plus the
   * p10/p90 band, so only those three are requested (the nine-decile map no longer comes back).
   */
  static final List<String> QUANTILES = List.of("0.1", "0.5", "0.9");

  private static final String MODEL_VERSION = "timesfm-3.0";
  private static final String INTERRUPTED_MESSAGE = "service restarted before completion";

  private final ForecastRunRepository runs;
  private final MetricQueryService queries;
  private final MetricCatalog catalog;
  private final ForecastModelClient client;
  private final TaskExecutor executor;
  private final TimesFmProperties properties;
  private final Clock clock;

  /** The venue-zone clock is supplied by configuration; forecasting stays a leaf. */
  @Autowired
  public ForecastService(
      ForecastRunRepository runs,
      MetricQueryService queries,
      MetricCatalog catalog,
      ForecastModelClient client,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      TimesFmProperties properties,
      @Qualifier("venueCalendarClock") Clock clock) {
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
    if (targetMetric == null) {
      throw new IllegalArgumentException("targetMetric is required");
    }
    if (historyFrom == null) {
      throw new IllegalArgumentException("historyFrom is required");
    }
    if (!properties.enabled()) {
      throw new IllegalStateException("forecasting is not enabled");
    }
    if (horizonDays < 1 || horizonDays > 90) {
      throw new IllegalArgumentException("horizon must be between 1 and 90 days");
    }
    MetricId target = requireForecastable(targetMetric);
    if (covariateMetrics == null || !covariateMetrics.isEmpty()) {
      throw new IllegalArgumentException(
          "The net-revenue model uses fixed supporting inputs; custom covariates are not supported");
    }
    LocalDate historyTo = LocalDate.now(clock);
    if (historyFrom.isAfter(historyTo)) {
      throw new IllegalArgumentException(
          "historyFrom must not be after historyTo (" + historyTo + ")");
    }
    TimeRange range = new TimeRange(historyFrom, historyTo, Calendar.CALENDAR);
    ForecastInputSnapshot snapshot = captureInput(target, range, horizonDays);
    long resolvedPoints =
        snapshot.request().net().stream().filter(java.util.Objects::nonNull).count();
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
    run.captureInput(snapshot);
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
      if (run.inputSnapshot() == null) {
        run.markFailed("Original input snapshot unavailable; create a new forecast");
        runs.save(run);
        return;
      }
      ForecastModelRequest request = run.inputSnapshot().request();
      ForecastModelResponse response = client.forecast(request);
      run.markSucceeded(
          toForecastDays(response), response.diagnostics(), MODEL_VERSION, elapsedMs(startNanos));
    } catch (RuntimeException e) {
      run.markFailed(failureMessage(e));
    }
    runs.save(run);
  }

  /** Fetches one metric as a single daily series, rejecting ranked-list or multi-series results. */
  MetricSeries series(MetricId id, TimeRange range) {
    return result(id, range).series().getFirst();
  }

  private TimeSeriesResult result(MetricId id, TimeRange range) {
    MetricResult result = queries.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
    if (!(result instanceof TimeSeriesResult timeSeries) || timeSeries.series().size() != 1) {
      throw new IllegalStateException(
          "metric " + id.value() + " must return exactly one time series");
    }
    return timeSeries;
  }

  private ForecastInputSnapshot captureInput(MetricId target, TimeRange history, int horizon) {
    Map<String, com.goldys.platform.semantic.catalog.MetricProvenance> provenance =
        new LinkedHashMap<>();
    List<String> notices = new ArrayList<>();
    TimeRange future =
        new TimeRange(history.from(), history.to().plusDays(horizon), Calendar.CALENDAR);
    List<MetricPoint> net = captureSeries(target, history, provenance, notices);
    List<MetricPoint> food = captureSeries(MetricId.SALES_FOOD, history, provenance, notices);
    List<MetricPoint> bev = captureSeries(MetricId.SALES_BEV, history, provenance, notices);
    List<MetricPoint> covers =
        captureSeries(MetricId.RESERVATIONS_BOOKED_COVERS, future, provenance, notices);
    List<MetricPoint> bookings =
        captureSeries(MetricId.RESERVATIONS_BOOKINGS, future, provenance, notices);
    notices.add(
        "Forecast uses an immutable input snapshot; incomplete inputs remain gaps, not zero");
    return new ForecastInputSnapshot(
        ForecastSeriesAssembler.request(
            ForecastSeriesAssembler.axis(history.from(), future.to()),
            net,
            covers,
            bookings,
            food,
            bev,
            horizon,
            QUANTILES),
        provenance,
        notices,
        clock.instant());
  }

  private List<MetricPoint> captureSeries(
      MetricId id,
      TimeRange range,
      Map<String, com.goldys.platform.semantic.catalog.MetricProvenance> provenance,
      List<String> notices) {
    TimeSeriesResult result = result(id, range);
    provenance.put(id.value(), result.provenance());
    result.notices().forEach(n -> notices.add(id.value() + ": " + n));
    return result.series().getFirst().points();
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
    if (!ForecastableMetrics.forecastable(id)) {
      throw new IllegalArgumentException("metric is not forecastable: " + metric);
    }
    // Defence in depth: the catalogue is the source of truth for what a metric is, so a catalogued
    // id that is missing from the catalogue (drift between enum and catalogue) is rejected too.
    catalog.definition(id);
    return id;
  }

  /**
   * Reconstructs the persisted {@link ForecastDay}s from the wire's per-day rows: the wire carries
   * only median/p10/p90 (no nine-decile map), so the quantile map is rebuilt from those three, and
   * the wire's 0/1/null flag integers are bridged to {@code Boolean} drivers.
   */
  private List<ForecastDay> toForecastDays(ForecastModelResponse response) {
    List<ForecastDay> days = new ArrayList<>(response.forecast().size());
    for (ForecastModelResponse.Day day : response.forecast()) {
      Map<String, BigDecimal> quantiles = new LinkedHashMap<>();
      quantiles.put("0.1", day.p10());
      quantiles.put("0.5", day.median());
      quantiles.put("0.9", day.p90());
      days.add(
          new ForecastDay(
              day.date(),
              quantiles,
              day.food(),
              day.bev(),
              day.baseline(),
              day.bookedCovers(),
              flag(day.publicHoliday()),
              day.publicHolidayName(),
              flag(day.publicHolidayEve()),
              flag(day.dayAfterPublicHoliday()),
              flag(day.schoolHoliday()),
              flag(day.uniInSession()),
              day.serviceTempMaxC(),
              day.serviceRainMm(),
              day.weatherSource(),
              day.observedBookedCovers()));
    }
    return days;
  }

  /** The wire's 0/1/null flag integer as a {@code Boolean} (1 → true, 0 → false, null → null). */
  private static Boolean flag(Integer value) {
    return value == null ? null : value == 1;
  }

  private String failureMessage(RuntimeException e) {
    // ForecastModelException messages are already operator-facing (no payload, credentials, or
    // tokens). Anything else gets a class-only label: its message may carry a URL, token, or
    // payload.
    if (e instanceof ForecastModelException) {
      return e.getMessage();
    }
    return "unexpected error: " + e.getClass().getSimpleName();
  }

  private long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
