package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens, annotates, and closes ingestion runs.
 *
 * <p>The terminal state is derived from recorded evidence — persisted payloads and recorded
 * failures — rather than guessed from an empty result, so "the connector broke" and "the source had
 * nothing new" stay distinguishable.
 */
@Service
class IngestionRunService {
  private final IngestionRunRepository runs;
  private final IngestionFailureRepository failures;

  IngestionRunService(IngestionRunRepository runs, IngestionFailureRepository failures) {
    this.runs = runs;
    this.failures = failures;
  }

  @Transactional
  UUID start(String sourceSystem, String connectorName, String inputWatermark, Instant startedAt) {
    return runs.save(IngestionRun.start(sourceSystem, connectorName, inputWatermark, startedAt))
        .id();
  }

  @Transactional
  void recordFetched(UUID ingestionRunId) {
    IngestionRun run = require(ingestionRunId);
    run.recordFetched();
    runs.save(run);
  }

  /**
   * Records a failure in its own transaction so it survives whatever happens to the rest of the
   * run. {@code detail} is operator-facing text and must never contain payload contents,
   * credentials, or tokens.
   */
  @Transactional
  void recordFailure(UUID ingestionRunId, String failureType, String detail, Instant occurredAt) {
    IngestionRun run = require(ingestionRunId);
    failures.save(
        IngestionFailure.record(
            ingestionRunId, run.sourceSystem(), failureType, detail, occurredAt));
  }

  @Transactional
  void complete(UUID ingestionRunId, String outputWatermark, Instant completedAt) {
    IngestionRun run = require(ingestionRunId);
    long failureCount = failures.countByIngestionRunId(ingestionRunId);

    IngestionStatus status;
    if (run.persistedCount() == 0 && failureCount > 0) {
      status = IngestionStatus.FAILED;
    } else if (failureCount > 0) {
      status = IngestionStatus.PARTIAL;
    } else if (run.persistedCount() == 0) {
      status = IngestionStatus.NO_NEW_DATA;
    } else {
      status = IngestionStatus.SUCCESS;
    }

    String failureSummary = failureCount == 0 ? null : failureCount + " failure(s) recorded";
    run.complete(status, outputWatermark, failureSummary, completedAt);
    runs.save(run);
  }

  private IngestionRun require(UUID ingestionRunId) {
    return runs.findById(ingestionRunId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown ingestion run " + ingestionRunId));
  }
}
