package com.goldys.platform.ingestion;

import com.goldys.platform.ingestion.port.SourceConnector;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Public ingestion entry point. The package-private ledger services stay internal to this package;
 * controllers and other modules call this facade instead.
 */
@Service
public class IngestionService {
  private static final Clock CLOCK = Clock.systemUTC();

  /** Raw-only connector whose runs are stored evidence, not data delivery (PDF enrichment). */
  private static final String PDF_ENRICHMENT_CONNECTOR = "ctb-invoice-pdf";

  private final IngestionRunService runs;
  private final RawPayloadService payloads;
  private final IngestionRunRepository runRepository;
  private final IngestionFailureRepository failureRepository;
  private final RawRecordRepository rawRecords;
  private final IngestionStageRepository stages;
  private final ConnectorRunner connectorRunner;
  private final Map<String, SourceConnector> connectors;
  private final TransactionTemplate transactionTemplate;
  private final JdbcTemplate jdbcTemplate;

  public IngestionService(
      IngestionRunService runs,
      RawPayloadService payloads,
      IngestionRunRepository runRepository,
      IngestionFailureRepository failureRepository,
      RawRecordRepository rawRecords,
      IngestionStageRepository stages,
      ConnectorRunner connectorRunner,
      List<SourceConnector> connectors,
      PlatformTransactionManager transactionManager,
      JdbcTemplate jdbcTemplate) {
    this.runs = runs;
    this.payloads = payloads;
    this.runRepository = runRepository;
    this.failureRepository = failureRepository;
    this.rawRecords = rawRecords;
    this.stages = stages;
    this.connectorRunner = connectorRunner;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.jdbcTemplate = jdbcTemplate;
    this.connectors =
        connectors.stream()
            .collect(
                Collectors.toMap(
                    connector -> connector.sourceSystem().trim().toUpperCase(Locale.ROOT),
                    connector -> connector));
  }

  /** Run a pull connector now and return its resulting run summary. */
  public IngestionRunSummary runConnector(String source) {
    return runConnector(source, null);
  }

  /**
   * As {@link #runConnector(String)}, but reports the run's terminal status to {@code onComplete}
   * once the asynchronous fetch finishes (for cron monitoring). If a run is already in flight no
   * new run starts, and {@code onComplete} receives {@code null} immediately: the tick was skipped,
   * not failed. A {@code null} callback behaves exactly like {@link #runConnector(String)}.
   */
  public IngestionRunSummary runConnector(String source, Consumer<IngestionStatus> onComplete) {
    return runConnector(source, null, onComplete);
  }

  /**
   * Runs a pull connector from a caller-chosen {@code watermark} and reports the terminal status by
   * name ({@code SUCCESS}, {@code PARTIAL}, ...; {@code null} when an in-flight run meant no new
   * run started). For callers outside this package, which cannot name {@link IngestionStatus}: the
   * OpenTable assisted session uses it to pass its sync mode and follow the run to completion.
   */
  public IngestionRunSummary runConnectorFrom(
      String source, String watermark, Consumer<String> onCompleteStatusName) {
    Consumer<IngestionStatus> onComplete =
        onCompleteStatusName == null
            ? null
            : status -> onCompleteStatusName.accept(status == null ? null : status.name());
    return runConnector(source, watermark, onComplete);
  }

  private IngestionRunSummary runConnector(
      String source, String watermark, Consumer<IngestionStatus> onComplete) {
    SourceConnector connector = connectors.get(source.trim().toUpperCase(Locale.ROOT));
    if (connector == null) {
      throw new IllegalArgumentException("Unknown source: " + source);
    }
    String sourceSystem = connector.sourceSystem();
    // The check for a RUNNING run and the start of a new one must be atomic, or a cron tick and a
    // "Run now" click can both see "no run in flight" and start two pulls. Serialize them with a
    // Postgres advisory transaction lock keyed on the source: the lock is released when this
    // transaction commits, and IngestionRunService.start commits its RUNNING row independently
    // (REQUIRES_NEW), so a concurrent caller that later acquires the lock sees it and skips.
    return transactionTemplate.execute(
        status -> {
          lockConnector(sourceSystem);
          var active = runRepository.findFirstBySourceSystemOrderByStartedAtDesc(sourceSystem);
          if (active.isPresent() && active.get().status() == IngestionStatus.RUNNING) {
            if (onComplete != null) {
              onComplete.accept(null);
            }
            return toSummary(active.get());
          }
          UUID runId =
              onComplete == null
                  ? connectorRunner.run(connector, watermark)
                  : connectorRunner.run(connector, watermark, onComplete);
          return runSummary(runId);
        });
  }

  /** Takes the source's advisory transaction lock on the current transaction's connection. */
  private void lockConnector(String sourceSystem) {
    jdbcTemplate.execute(
        (Connection con) -> {
          try (PreparedStatement ps =
              con.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, "run:" + sourceSystem);
            ps.execute();
          }
          return null;
        });
  }

  /** True when the source has a pull {@link SourceConnector} (i.e. "Run now" applies). */
  public boolean isRunnable(String source) {
    return connectors.containsKey(source.trim().toUpperCase(Locale.ROOT));
  }

  private IngestionRunSummary runSummary(UUID runId) {
    IngestionRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    return toSummary(run);
  }

  /** Persist a pushed payload (e.g. a webhook) as one completed ingestion run. */
  public UUID ingestPush(
      String sourceSystem,
      String connectorName,
      FetchMethod fetchMethod,
      String contentType,
      byte[] bytes,
      String characterEncoding,
      String fetcherIdentity) {
    return ingestPush(
        sourceSystem,
        connectorName,
        fetchMethod,
        contentType,
        bytes,
        characterEncoding,
        fetcherIdentity,
        null);
  }

  /** A stable delivery reference lets retries retain the original source filename. */
  public UUID ingestPush(
      String sourceSystem,
      String connectorName,
      FetchMethod fetchMethod,
      String contentType,
      byte[] bytes,
      String characterEncoding,
      String fetcherIdentity,
      String sourceRecordRef) {
    UUID runId = runs.start(sourceSystem, connectorName, null, CLOCK.instant());
    try {
      runs.recordFetched(runId);
      UUID rawId =
          payloads.persist(
              runId,
              sourceSystem,
              fetchMethod,
              contentType,
              bytes,
              characterEncoding,
              fetcherIdentity,
              sourceRecordRef);
      // SFTP delivery success requires its processing stage, not merely a durable raw capture.
      // A crash before that stage leaves RUNNING for the existing startup recovery to expose.
      if (sourceRecordRef == null || !sourceRecordRef.startsWith("sftp:")) {
        runs.complete(runId, null, CLOCK.instant());
      }
      return rawId;
    } catch (RuntimeException e) {
      // Record the failure and close the run so the fault is observable, not a dangling RUNNING.
      runs.recordFailure(
          runId, "UNEXPECTED", e.getClass().getName(), stackTraceOf(e), CLOCK.instant());
      IngestionFailureReporter.reportUnexpected(runId, sourceSystem, e);
      runs.complete(runId, null, CLOCK.instant());
      throw e;
    }
  }

  private static String stackTraceOf(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  /** Record a dataset-stage outcome for the run that owns {@code rawId}. */
  public void recordStage(UUID rawId, IngestionStageKind stage, IngestionStageOutcome outcome) {
    RawRecord record =
        rawRecords
            .findById(rawId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown raw record " + rawId));
    stages.save(
        IngestionStage.create(
            record.ingestionRunId(),
            record.sourceSystem(),
            record.fetcherIdentity(),
            stage,
            outcome,
            CLOCK.instant()));
    if (outcome == IngestionStageOutcome.FAILED) {
      runs.recordProcessingFailure(record.ingestionRunId(), stage, CLOCK.instant());
    }
    if (record.sourceRecordRef() != null
        && record.sourceRecordRef().startsWith("sftp:")
        && (outcome == IngestionStageOutcome.FAILED
            || stage == IngestionStageKind.CANONICALIZED
            || stage == IngestionStageKind.ENRICHED)) {
      runs.complete(record.ingestionRunId(), null, CLOCK.instant());
    }
  }

  /** Record a safe, operator-facing qualification without rejecting successfully parsed facts. */
  public void recordQualification(UUID rawId, String type, String detail) {
    RawRecord record =
        rawRecords
            .findById(rawId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown raw record " + rawId));
    runs.recordQualification(record.ingestionRunId(), type, detail, CLOCK.instant());
  }

  /** The latest run for each source. One query, indexed, regardless of ledger size. */
  public List<IngestionRunSummary> latestRunPerSource() {
    return runRepository.latestPerSource().stream().map(this::toSummary).toList();
  }

  /** The latest run for each (source, connector). One query, indexed, regardless of ledger size. */
  public List<IngestionRunSummary> latestRunPerConnector() {
    return runRepository.latestPerConnector().stream().map(this::toSummary).toList();
  }

  /** Latest failed attempt per retained SFTP file; a different successful file cannot hide it. */
  public List<IngestionRunSummary> outstandingSftpRuns() {
    return runRepository.outstandingSftpRuns().stream().map(this::toSummary).toList();
  }

  /** Shared current health: newer unrelated successes cannot conceal held delivery evidence. */
  public List<IngestionRunSummary> connectorHealthRuns() {
    Map<String, IngestionRunSummary> current = new LinkedHashMap<>();
    latestRunPerConnector().forEach(r -> current.put(r.connectorName(), r));
    Map<String, IngestionRunSummary> held = new LinkedHashMap<>();
    outstandingSftpRuns().forEach(r -> held.putIfAbsent(r.connectorName(), r));
    held.forEach(
        (name, failure) -> {
          IngestionRunSummary latest = current.get(name);
          current.put(
              name,
              latest != null && latest.status().equals("RUNNING")
                  ? new IngestionRunSummary(
                      latest.sourceSystem(),
                      name,
                      "RUNNING",
                      latest.startedAt(),
                      failure.failureSummary(),
                      failure.failure())
                  : failure);
        });
    return List.copyOf(current.values());
  }

  /** Bound automatic retries of identical failed deliveries; corrected bytes retry immediately. */
  public boolean deferSftpRetry(String deliveryRef, byte[] bytes) {
    return rawRecords
        .latestSftpAttempt(deliveryRef)
        .filter(a -> !a.getStatus().equals("SUCCESS"))
        .filter(a -> a.getFetchedAt().isAfter(CLOCK.instant().minus(Duration.ofHours(6))))
        .filter(a -> a.getPayloadSha256().equals(RawPayloadService.sha256Hex(bytes)))
        .isPresent();
  }

  private IngestionRunSummary toSummary(IngestionRun run) {
    return new IngestionRunSummary(
        run.sourceSystem(),
        run.connectorName(),
        run.status().name(),
        run.startedAt(),
        run.failureSummary(),
        latestFailure(run.id()));
  }

  private FailureDetail latestFailure(UUID runId) {
    List<IngestionFailure> runFailures =
        failureRepository.findByIngestionRunIdOrderByOccurredAtAsc(runId);
    if (runFailures.isEmpty()) {
      return null;
    }
    IngestionFailure latest = runFailures.get(runFailures.size() - 1);
    // Only these holds explain a failed canonicalization. A metadata warning must never mask
    // a later genuine processing failure (for example a sale-item write that rolled back).
    if (latest.failureType().equals("PROCESSING_CANONICALIZED_FAILED")) {
      latest =
          runFailures.stream()
              .filter(
                  f ->
                      f.failureType().equals("INVOICE_IDENTITY_WITHHELD")
                          || f.failureType().equals("INVOICE_REVISION_WITHHELD"))
              .reduce((a, b) -> b)
              .orElse(latest);
    }
    return new FailureDetail(
        latest.failureType(), latest.detail(), latest.occurredAt(), latest.stackTrace());
  }

  /**
   * Dashboard health metrics derived from the ledger.
   *
   * <p>Completeness is the share of <em>completed</em> data-delivery runs (any terminal status,
   * excluding raw-only {@code ctb-invoice-pdf} enrichment writes) that ran cleanly — {@code
   * SUCCESS} or {@code NO_NEW_DATA}. {@code PARTIAL} and {@code FAILED} both count against it,
   * since a partial run still carries a silent-gap risk. A dangling {@code RUNNING} row (a crash
   * that never completed) is excluded from both numbers: it is neither a clean run nor a measured
   * failure duration.
   *
   * <p>Time-to-detect is the mean {@code startedAt → completedAt} duration of runs that recorded a
   * failure ({@code FAILED} or {@code PARTIAL}). This is a proxy for "how long until a failed run
   * became visible": the ledger records failures synchronously and does not timestamp the moment a
   * human saw them, so run duration is the closest measurable stand-in.
   */
  public IngestionHealth health() {
    long completed = 0;
    long clean = 0;
    long failed = 0;
    for (StatusCount statusCount :
        runRepository.statusCountsExcludingConnector(PDF_ENRICHMENT_CONNECTOR)) {
      if (statusCount.status() != IngestionStatus.RUNNING) {
        completed += statusCount.count();
      }
      if (statusCount.status() == IngestionStatus.SUCCESS
          || statusCount.status() == IngestionStatus.NO_NEW_DATA) {
        clean += statusCount.count();
      }
      if (statusCount.status() == IngestionStatus.FAILED
          || statusCount.status() == IngestionStatus.PARTIAL) {
        failed += statusCount.count();
      }
    }

    Integer completeness = null;
    if (completed > 0) {
      completeness = (int) Math.round(100.0 * clean / completed);
    }

    String timeToDetect = null;
    if (failed > 0) {
      Double seconds = runRepository.avgFailedDurationSeconds();
      if (seconds != null) {
        timeToDetect = formatDuration(Duration.ofMillis((long) (seconds * 1000))) + " avg";
      }
    }

    return new IngestionHealth(completeness, timeToDetect);
  }

  /**
   * Daily connector-run activity for the dashboard trend, one point per day over the trailing
   * {@code days} days (inclusive of today), oldest first. Days with no runs are zero-filled so the
   * chart has a stable x-axis.
   */
  public List<IngestionActivityPoint> activity(int days) {
    if (days < 1) {
      return List.of();
    }
    LocalDate today = CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate();
    return activity(today.minusDays(days - 1L), today);
  }

  /**
   * Daily connector-run activity over the inclusive {@code from}–{@code to} range, oldest first.
   * Same zero-filling as {@link #activity(int)} but with an explicit window instead of a trailing
   * day count.
   */
  public List<IngestionActivityPoint> activity(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      return List.of();
    }
    Instant windowStart = from.atStartOfDay(ZoneOffset.UTC).toInstant();
    return activityWindow(runRepository.findByStartedAtGreaterThanEqual(windowStart), from, to);
  }

  /**
   * Bucket completed runs into UTC days over the inclusive {@code from}–{@code to} range, counting
   * {@code SUCCESS}/{@code NO_NEW_DATA} as clean and {@code FAILED}/{@code PARTIAL} as failed.
   * Dangling {@code RUNNING} rows and runs outside the window are ignored.
   */
  static List<IngestionActivityPoint> activityWindow(
      List<IngestionRun> runs, LocalDate from, LocalDate to) {
    Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      buckets.put(d, new int[2]); // [clean, failed]
    }

    for (IngestionRun run : runs) {
      if (run.status() == IngestionStatus.RUNNING) {
        continue;
      }
      int[] bucket = buckets.get(run.startedAt().atZone(ZoneOffset.UTC).toLocalDate());
      if (bucket == null) {
        continue;
      }
      if (run.status() == IngestionStatus.SUCCESS || run.status() == IngestionStatus.NO_NEW_DATA) {
        bucket[0]++;
      } else {
        bucket[1]++;
      }
    }

    List<IngestionActivityPoint> points = new ArrayList<>(buckets.size());
    buckets.forEach(
        (day, counts) ->
            points.add(new IngestionActivityPoint(day.toString(), counts[0], counts[1])));
    return points;
  }

  private static String formatDuration(Duration d) {
    long seconds = d.toSeconds();
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m";
    }
    long hours = minutes / 60;
    long remMinutes = minutes % 60;
    if (hours < 24) {
      return remMinutes == 0 ? hours + "h" : hours + "h " + remMinutes + "m";
    }
    long days = hours / 24;
    long remHours = hours % 24;
    return remHours == 0 ? days + "d" : days + "d " + remHours + "h";
  }
}
