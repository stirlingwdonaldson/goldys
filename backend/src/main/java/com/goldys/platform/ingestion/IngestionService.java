package com.goldys.platform.ingestion;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  public IngestionService(
      IngestionRunService runs, RawPayloadService payloads, IngestionRunRepository runRepository) {
    this.runs = runs;
    this.payloads = payloads;
    this.runRepository = runRepository;
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
    runs.recordFetched(runId);
    payloads.persist(
        runId, sourceSystem, fetchMethod, contentType, bytes, characterEncoding, fetcherIdentity);
    runs.complete(runId, null, CLOCK.instant());
    return runId;
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
