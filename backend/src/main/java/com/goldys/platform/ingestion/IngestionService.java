package com.goldys.platform.ingestion;

import java.time.Clock;
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

  public IngestionService(IngestionRunService runs, RawPayloadService payloads) {
    this.runs = runs;
    this.payloads = payloads;
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
}
