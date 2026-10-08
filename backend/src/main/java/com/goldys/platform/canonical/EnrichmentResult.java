package com.goldys.platform.canonical;

/**
 * The outcome of applying PDF enrichment to one canonical line, so the caller can flag anomalies (a
 * PDF-only line with no CSV counterpart) instead of silently no-op'ing.
 */
public enum EnrichmentResult {
  /** A successor line was written carrying the PDF's enrichment fields. */
  ENRICHED,
  /** The fields were already present — no new version written. */
  UNCHANGED,
  /** No current CSV line matched the PDF line's (invoice number, stock code). */
  NO_MATCH,
  /** More than one current line matched (a duplicate stock code); skipped rather than guessed. */
  AMBIGUOUS
}
