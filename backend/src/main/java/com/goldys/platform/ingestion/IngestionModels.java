package com.goldys.platform.ingestion;

/**
 * Lifecycle state of a single connector run.
 *
 * <p>Partial success is deliberately distinct from success, failure, and no-new-data so an operator
 * can never mistake "some pages failed" for "nothing changed at the source".
 */
enum IngestionStatus {
  RUNNING,
  SUCCESS,
  PARTIAL,
  FAILED,
  NO_NEW_DATA
}
