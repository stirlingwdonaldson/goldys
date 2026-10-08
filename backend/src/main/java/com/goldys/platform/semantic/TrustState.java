package com.goldys.platform.semantic;

/**
 * How a resolved value earned — or failed to earn — the operator's trust.
 *
 * <p>Ordered from strongest to weakest evidence: a verified agreement, a deterministic rule, a
 * manual override, a single source, a contradiction, partial data, or nothing at all.
 */
public enum TrustState {
  VERIFIED,
  RESOLVED_BY_RULE,
  MANUALLY_OVERRIDDEN,
  SINGLE_SOURCE,
  CONFLICTED,
  INCOMPLETE,
  NOT_RECEIVED
}
