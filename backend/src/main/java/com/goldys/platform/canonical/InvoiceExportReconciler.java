package com.goldys.platform.canonical;

import java.math.BigDecimal;

/**
 * Classifies whether a CTB invoice CSV export is complete with respect to the invoice header total,
 * i.e. whether the export omitted lines.
 *
 * <p>Pure and dependency-free: it holds no state and touches no repositories. The caller supplies
 * the header total (ex-tax), the summed CSV line amounts, the summed PDF line amounts (nullable,
 * when a PDF was not available) and a scale-safe tolerance.
 */
public final class InvoiceExportReconciler {

  public enum Outcome {
    /** The header total is missing; there is nothing to reconcile against. */
    NO_HEADER,
    /** The CSV line sum matches the header within tolerance; the export is complete. */
    CSV_COMPLETE,
    /** The CSV line sum is short, but the PDF line sum matches the header within tolerance. */
    PDF_COMPLETE_CSV_SHORT,
    /** Neither the CSV nor the PDF line sum reconciles with the header. */
    UNRESOLVED
  }

  private InvoiceExportReconciler() {}

  public static Outcome classify(
      BigDecimal headerExTax, BigDecimal csvLineSum, BigDecimal pdfLineSum, BigDecimal tolerance) {
    if (headerExTax == null) {
      return Outcome.NO_HEADER;
    }
    if (withinTolerance(csvLineSum, headerExTax, tolerance)) {
      return Outcome.CSV_COMPLETE;
    }
    if (pdfLineSum != null && withinTolerance(pdfLineSum, headerExTax, tolerance)) {
      return Outcome.PDF_COMPLETE_CSV_SHORT;
    }
    return Outcome.UNRESOLVED;
  }

  private static boolean withinTolerance(
      BigDecimal actual, BigDecimal expected, BigDecimal tolerance) {
    // BigDecimal.equals() compares scale as well as value, so use subtract().abs().compareTo().
    return actual.subtract(expected).abs().compareTo(tolerance) <= 0;
  }
}
