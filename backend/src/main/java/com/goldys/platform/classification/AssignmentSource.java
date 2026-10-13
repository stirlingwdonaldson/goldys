package com.goldys.platform.classification;

/**
 * How a product→category assignment was decided: Jev's {@code Choice} model, a staff manual
 * override, or a cluster-derived guess.
 */
public enum AssignmentSource {
  JEV,
  MANUAL,
  CLUSTERED
}
