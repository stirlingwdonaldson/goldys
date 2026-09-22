package com.goldys.platform.ingestion;

import com.goldys.platform.ingestion.port.SourceConnector;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Public ingestion entry point. The package-private ledger services stay internal to this package;
 * controllers and other modules call this facade instead.
 */
@Service
public class IngestionService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final IngestionRunService runs;
  private final RawPayloadService payloads;
  private final IngestionRunRepository runRepository;
  private final ConnectorRunner connectorRunner;
  private final Map<String, SourceConnector> connectors;

  public IngestionService(
      IngestionRunService runs,
      RawPayloadService payloads,
      IngestionRunRepository runRepository,
      ConnectorRunner connectorRunner,
      List<SourceConnector> connectors) {
    this.runs = runs;
    this.payloads = payloads;
    this.runRepository = runRepository;
    this.connectorRunner = connectorRunner;
    this.connectors =
        connectors.stream()
            .collect(
                Collectors.toMap(
                    connector -> connector.sourceSystem().trim().toUpperCase(Locale.ROOT),
                    connector -> connector));
  }

  /** Run a pull connector now and return its resulting run summary. */
  public IngestionRunSummary runConnector(String source) {
    SourceConnector connector = connectors.get(source.trim().toUpperCase(Locale.ROOT));
    if (connector == null) {
      throw new IllegalArgumentException("Unknown source: " + source);
    }
    UUID runId = connectorRunner.run(connector, null);
    return runSummary(runId);
  }

  private IngestionRunSummary runSummary(UUID runId) {
    IngestionRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    return new IngestionRunSummary(
        run.sourceSystem(),
        run.connectorName(),
        run.status().name(),
        run.startedAt(),
        run.failureSummary());
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
              fetcherIdentity);
      runs.complete(runId, null, CLOCK.instant());
      return rawId;
    } catch (RuntimeException e) {
      // Record the failure and close the run so the fault is observable, not a dangling RUNNING.
      runs.recordFailure(runId, "UNEXPECTED", e.getClass().getName(), CLOCK.instant());
      runs.complete(runId, null, CLOCK.instant());
      throw e;
    }
  }

  /** The latest run for each source, newest first by start time. */
  public List<IngestionRunSummary> latestRunPerSource() {
    Map<String, IngestionRun> latest = new LinkedHashMap<>();
    for (IngestionRun run : runRepository.findAllByOrderByStartedAtDesc()) {
      latest.putIfAbsent(run.sourceSystem(), run);
    }
    return latest.values().stream()
        .map(
            r ->
                new IngestionRunSummary(
                    r.sourceSystem(),
                    r.connectorName(),
                    r.status().name(),
                    r.startedAt(),
                    r.failureSummary()))
        .toList();
  }

  /**
   * Dashboard health metrics derived from the ledger.
   *
   * <p>Completeness is the share of <em>completed</em> runs (any terminal status) that ran cleanly
   * — {@code SUCCESS} or {@code NO_NEW_DATA}. {@code PARTIAL} and {@code FAILED} both count against
   * it, since a partial run still carries a silent-gap risk. A dangling {@code RUNNING} row (a
   * crash that never completed) is excluded from both numbers: it is neither a clean run nor a
   * measured failure duration.
   *
   * <p>Time-to-detect is the mean {@code startedAt → completedAt} duration of runs that recorded a
   * failure ({@code FAILED} or {@code PARTIAL}). This is a proxy for "how long until a failed run
   * became visible": the ledger records failures synchronously and does not timestamp the moment a
   * human saw them, so run duration is the closest measurable stand-in.
   */
  public IngestionHealth health() {
    List<IngestionRun> completed =
        runRepository.findAllByOrderByStartedAtDesc().stream()
            .filter(r -> r.status() != IngestionStatus.RUNNING)
            .toList();

    Integer completeness = null;
    if (!completed.isEmpty()) {
      long clean =
          completed.stream()
              .filter(
                  r ->
                      r.status() == IngestionStatus.SUCCESS
                          || r.status() == IngestionStatus.NO_NEW_DATA)
              .count();
      completeness = (int) Math.round(100.0 * clean / completed.size());
    }

    String timeToDetect = null;
    List<IngestionRun> failed =
        completed.stream()
            .filter(
                r -> r.status() == IngestionStatus.FAILED || r.status() == IngestionStatus.PARTIAL)
            .toList();
    if (!failed.isEmpty()) {
      long totalMillis =
          failed.stream()
              .mapToLong(r -> Duration.between(r.startedAt(), r.completedAt()).toMillis())
              .sum();
      timeToDetect = formatDuration(Duration.ofMillis(totalMillis / failed.size())) + " avg";
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
    Instant now = CLOCK.instant();
    LocalDate firstDay = now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(days - 1L);
    Instant windowStart = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
    return activityWindow(runRepository.findByStartedAtGreaterThanEqual(windowStart), now, days);
  }

  /**
   * Bucket completed runs into UTC days over the trailing {@code days} days ending at {@code now},
   * counting {@code SUCCESS}/{@code NO_NEW_DATA} as clean and {@code FAILED}/{@code PARTIAL} as
   * failed. Dangling {@code RUNNING} rows and runs outside the window are ignored.
   */
  static List<IngestionActivityPoint> activityWindow(
      List<IngestionRun> runs, Instant now, int days) {
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate firstDay = today.minusDays(days - 1L);

    Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
    for (int i = 0; i < days; i++) {
      buckets.put(firstDay.plusDays(i), new int[2]); // [clean, failed]
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
