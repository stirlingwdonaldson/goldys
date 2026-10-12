package com.goldys.platform.forecasting;

/** Lifecycle of a persisted forecast run, from submission through completion or failure. */
public enum ForecastRunStatus {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED
}
