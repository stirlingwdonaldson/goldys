package com.goldys.platform.ingestion;

import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.IngestionSink;
import com.goldys.platform.ingestion.port.SourceConnector;
import com.goldys.platform.metrics.OperationalMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Runs one connector against the ingestion ledger.
 *
 * <p>The fetch executes asynchronously so a slow connector never blocks the "run now" request.
 * Deliberately not transactional: each payload and each failure commits on its own, so a connector
 * that dies halfway leaves the evidence it already produced and a run marked PARTIAL rather than an
 * all-or-nothing rollback.
 */
@Service
class ConnectorRunner {
  private static final Clock CLOCK = Clock.systemUTC();
  private static final Logger log = LoggerFactory.getLogger(ConnectorRunner.class);

  private final IngestionRunService runs;
  private final RawPayloadService payloads;
  private final TaskExecutor executor;
  private final OperationalMetrics metrics;

  ConnectorRunner(
      IngestionRunService runs,
      RawPayloadService payloads,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      OperationalMetrics metrics) {
    this.runs = runs;
    this.payloads = payloads;
    this.executor = executor;
    this.metrics = metrics;
  }

  /** Starts a run and executes the connector asynchronously; returns the run id immediately. */
  UUID run(SourceConnector connector, String watermark) {
    UUID runId =
        runs.start(connector.sourceSystem(), connector.connectorName(), watermark, CLOCK.instant());
    executor.execute(() -> execute(runId, connector, watermark));
    return runId;
  }

  private void execute(UUID runId, SourceConnector connector, String watermark) {
    Timer.Sample sample = metrics.start();
    IngestionSink sink =
        payload -> {
          runs.recordFetched(runId);
          UUID rawId =
              payloads.persist(
                  runId,
                  connector.sourceSystem(),
                  payload.fetchMethod(),
                  payload.contentType(),
                  payload.bytes(),
                  payload.characterEncoding(),
                  payload.fetcherIdentity());
          metrics.payloadIngested(connector.sourceSystem());
          return rawId;
        };

    try {
      connector.fetch(watermark, sink);
    } catch (ConnectorFetchException e) {
      metrics.connectorFailure(connector.sourceSystem());
      runs.recordFailure(runId, e.failureType(), e.getMessage(), stackTraceOf(e), CLOCK.instant());
    } catch (RuntimeException e) {
      // An unclassified fault may carry anything in its message - a URL with a token, a fragment
      // of payload. The stack trace is stored deliberately (accepted tradeoff: the operator who
      // runs connectors already holds those secrets, and the ledger is append-only/trusted), but
      // the run is still closed rather than left RUNNING.
      log.warn("Connector {} failed unexpectedly", connector.sourceSystem(), e);
      metrics.connectorFailure(connector.sourceSystem());
      runs.recordFailure(
          runId, "UNEXPECTED", e.getClass().getName(), stackTraceOf(e), CLOCK.instant());
    } finally {
      metrics.stopConnector(sample, connector.sourceSystem());
    }

    // This port carries no output watermark yet, so an unchanged run keeps the one it started
    // from rather than silently resetting the source position to null.
    runs.complete(runId, watermark, CLOCK.instant());
  }

  private static String stackTraceOf(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
