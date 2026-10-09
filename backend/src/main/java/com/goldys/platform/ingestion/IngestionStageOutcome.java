package com.goldys.platform.ingestion;

/**
 * Whether a dataset's stage produced rows ({@link #SUCCESS}), produced none ({@link #EMPTY}), or
 * errored ({@link #FAILED}).
 */
public enum IngestionStageOutcome {
  SUCCESS,
  EMPTY,
  FAILED
}
