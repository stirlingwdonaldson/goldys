package com.goldys.platform.connectors.ctb;

/**
 * The invoice-ingestion anomalies surfaced as first-class flags (spec §8, plus the ambiguous-PDF
 * case the measured data revealed). Stored as the flag's enum name on {@link InvoiceIngestFlag}.
 */
public enum InvoiceIngestFlagType {
  /**
   * A PDF has no extractable text layer (a scan). Reserved — OCR handles scans transparently, so a
   * scan only surfaces as {@link #PDF_UNPARSEABLE} if OCR also fails.
   */
  SCANNED_PDF,
  /** A PDF's text could not be reduced to any usable line items. */
  PDF_UNPARSEABLE,
  /** A parsed PDF line has no matching CSV line (not canonicalized). */
  PDF_ONLY_LINE,
  /**
   * A PDF line's total disagrees with the CSV line total (CSV wins). Reserved — the PDF line
   * contract carries no total to compare against the CSV's LineTotalExTax yet.
   */
  PDF_CSV_MISMATCH,
  /** A CSV row's PDF filename is absent from the drop. */
  MISSING_PDF,
  /**
   * More than one PDF maps to one invoice, or the invoice identity is indeterminate: a dropped PDF
   * whose filename and printed invoice number both fail to resolve to an invoice in the CTB export
   * (raised once per PDF, not per line).
   */
  AMBIGUOUS_PDF,
  /**
   * The CSV export omitted lines the PDF carried and gap recovery could not materialise them (no
   * header to reconcile against, the line sums do not reconcile, or the PDF-only lines would push
   * the invoice total past the header).
   */
  INVOICE_EXPORT_INCOMPLETE
}
