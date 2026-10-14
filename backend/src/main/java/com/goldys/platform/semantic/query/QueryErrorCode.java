package com.goldys.platform.semantic.query;

/** Machine-readable error codes for the relational query path. */
public enum QueryErrorCode {
  UNKNOWN_VERSION,
  UNKNOWN_DATASET,
  UNKNOWN_FIELD,
  UNKNOWN_RELATIONSHIP,
  TYPE_MISMATCH,
  INVALID_PLAN,
  FANOUT_RISK,
  COMPLEXITY_EXCEEDED,
  NOT_PERMITTED,
  LIMIT_EXCEEDED,
  EXECUTION_FAILED
}
