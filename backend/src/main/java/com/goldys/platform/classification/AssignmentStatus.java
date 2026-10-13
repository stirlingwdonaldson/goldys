package com.goldys.platform.classification;

/**
 * Lifecycle of a single product→category assignment: confidently assigned, awaiting review, or
 * none.
 */
public enum AssignmentStatus {
  ASSIGNED,
  NEEDS_REVIEW,
  UNASSIGNED
}
