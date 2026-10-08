package com.goldys.platform.connectors.ctb;

/**
 * The invoice-ingestion anomalies surfaced as first-class flags (spec §8, plus the ambiguous-PDF
 * case the measured data revealed). Stored as the flag's enum name on {@link InvoiceIngestFlag}.
 */
public enum InvoiceIngestFlagType {
  /** A PDF has no extractable text layer (a scan) — the OCR fallback path applies. */
  SCANNED_PDF,
  /** A PDF's text could not be reduced to any usable line items. */
  PDF_UNPARSEABLE,
  /** A parsed PDF line has no matching CSV line (not canonicalized). */
  PDF_ONLY_LINE,
  /** A PDF line's total disagrees with the CSV line total (CSV wins). */
  PDF_CSV_MISMATCH,
  /** A CSV row's PDF filename is absent from the drop. */
  MISSING_PDF,
  /** More than one PDF maps to one invoice, or the invoice identity is indeterminate. */
  AMBIGUOUS_PDF
}
