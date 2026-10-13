package com.goldys.platform.classification;

/** Lifecycle of a persisted classification run, from start through success, failure, or partial. */
public enum ClassificationRunStatus {
  RUNNING,
  SUCCEEDED,
  FAILED,
  PARTIAL
}
