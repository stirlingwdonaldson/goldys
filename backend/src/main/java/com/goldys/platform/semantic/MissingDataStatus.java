package com.goldys.platform.semantic;

/**
 * Why a period carries no value, in venue-friendly language.
 *
 * <p>Distinguishes a genuine zero from the several ways data can be absent, so an operator never
 * misreads a gap as a real number.
 */
public enum MissingDataStatus {
  ZERO,
  UNKNOWN,
  NOT_RECEIVED,
  UNRESOLVED,
  NOT_APPLICABLE,
  NOT_PERMITTED
}
