package com.goldys.platform.ingestion;

/**
 * Signals that an ingestion run was asked to accept another write after it had already reached a
 * terminal state.
 *
 * <p>Distinct from a generic {@link IllegalStateException} so {@link ConnectorRunner} can tell "the
 * run was closed underneath me — e.g. by {@link IngestionRunService#recoverDanglingRuns} running on
 * a freshly started instance while my asynchronous fetch was still in flight — apart from a genuine
 * connector fault. The former is a lifecycle race, not a source failure, and must not be recorded
 * as an {@code UNEXPECTED} failure that would mislead an operator into re-checking the connection.
 */
final class RunAlreadyCompletedException extends IllegalStateException {
  RunAlreadyCompletedException(String message) {
    super(message);
  }
}
