package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Public write facade for canonical invoice line items, so connectors can record parsed facts. */
@Service
public class CanonicalInvoiceLineIngest {
  private final CanonicalInvoiceLineService service;

  public CanonicalInvoiceLineIngest(CanonicalInvoiceLineService service) {
    this.service = service;
  }

  public void record(InvoiceLineInput input) {
    service.record(input);
  }

  /**
   * One-off re-key: resolves the parent invoice reference for every current line whose {@code
   * invoiceLogicalId} is null (legacy rows). Orphans and ambiguous invoice numbers are left null.
   * Returns the number of lines re-keyed.
   */
  public int backfillInvoiceLogicalId() {
    return service.backfillInvoiceLogicalId();
  }

  /**
   * Closes the current CSV-sourced lines of one invoice whose {@code sourceRecordRef} is not in
   * {@code currentSourceRefs} (lines the re-ingested export no longer carries). Returns the number
   * of lines closed.
   */
  public int convergeCsvLines(UUID invoiceLogicalId, Set<String> currentSourceRefs) {
    return service.convergeCsvLines(invoiceLogicalId, currentSourceRefs);
  }

  /**
   * Closes the current PDF-derived lines of one invoice (supersede, never delete), so the CSV's
   * now-complete lines are the sole contributors. Returns the number of lines closed.
   */
  public int supersedePdfOriginLines(String invoiceNumber) {
    return service.supersedePdfOriginLines(invoiceNumber);
  }

  /**
   * Prunes the current PDF-derived lines of one invoice down to {@code allowedSum} (the CSV's
   * remaining shortfall), keeping lines in a deterministic order and superseding the excess.
   * Returns the number of lines closed. Used when the CSV export is only partly fixed.
   */
  public int prunePdfOriginLines(
      String invoiceNumber, BigDecimal allowedSum, BigDecimal tolerance) {
    return service.prunePdfOriginLines(invoiceNumber, allowedSum, tolerance);
  }
}
