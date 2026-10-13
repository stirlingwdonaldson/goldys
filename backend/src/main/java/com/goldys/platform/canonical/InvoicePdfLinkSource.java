package com.goldys.platform.canonical;

/** Where an invoice→PDF link came from. Stored as the enum name on {@link InvoicePdfLink}. */
public enum InvoicePdfLinkSource {
  /** The live enrichment path: a newly ingested PDF matched to its canonical invoice. */
  ENRICHMENT,
  /** The backfill runner: a link re-established from already-ingested data. */
  BACKFILL
}
