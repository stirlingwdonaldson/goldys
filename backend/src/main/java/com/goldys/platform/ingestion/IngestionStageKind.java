package com.goldys.platform.ingestion;

/** The observable pipeline stages a dataset passes through after its raw bytes are stored. */
public enum IngestionStageKind {
  RAW_STORED,
  PARSED,
  CANONICALIZED,
  ENRICHED
}
