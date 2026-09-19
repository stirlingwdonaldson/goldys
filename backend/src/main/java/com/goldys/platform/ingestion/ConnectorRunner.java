package com.goldys.platform.ingestion;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Runs one connector against the ingestion ledger.
 *
 * <p>Deliberately not transactional: each payload and each failure commits on its own, so a
 * connector that dies halfway leaves the evidence it already produced and a run marked PARTIAL
 * rather than an all-or-nothing rollback.
 */
@Service
class ConnectorRunner {
  private static final Clock CLOCK = Clock.systemUTC();

  private final IngestionRunService runs;
  private final RawPayloadService payloads;

  ConnectorRunner(IngestionRunService runs, RawPayloadService payloads) {
    this.runs = runs;
    this.payloads = payloads;
  }

  UUID run(SourceConnector connector, String watermark) {
    UUID runId =
        runs.start(connector.sourceSystem(), connector.connectorName(), watermark, CLOCK.instant());

    IngestionSink sink =
        payload -> {
          runs.recordFetched(runId);
          return payloads.persist(
              runId,
              connector.sourceSystem(),
              payload.fetchMethod(),
              payload.contentType(),
              payload.bytes(),
              payload.characterEncoding(),
              payload.fetcherIdentity());
        };

    try {
      connector.fetch(watermark, sink);
    } catch (ConnectorFetchException e) {
      runs.recordFailure(runId, e.failureType(), e.getMessage(), CLOCK.instant());
    } catch (RuntimeException e) {
      // An unclassified fault may carry anything in its message - a URL with a token, a fragment
      // of payload - so only the exception type is recorded, and the run is closed before the
      // exception continues to the caller.
      runs.recordFailure(runId, "UNEXPECTED", e.getClass().getName(), CLOCK.instant());
      runs.complete(runId, watermark, CLOCK.instant());
      throw e;
    }

    // This port carries no output watermark yet, so an unchanged run keeps the one it started
    // from rather than silently resetting the source position to null.
    runs.complete(runId, watermark, CLOCK.instant());
    return runId;
  }
}
