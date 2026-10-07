package com.goldys.platform.semantic;

/** Freshness of the underlying source data relative to its expected ingestion cadence. */
public enum FreshnessState {
  FRESH,
  STALE,
  SOURCE_FAILURE,
  UNKNOWN
}
