package com.goldys.platform.ingestion;

import com.goldys.platform.ingestion.port.SourceConnector;
import java.time.Clock;
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
}
