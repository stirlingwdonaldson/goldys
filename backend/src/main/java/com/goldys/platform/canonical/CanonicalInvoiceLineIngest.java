package com.goldys.platform.canonical;

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
   * Closes the current PDF-derived lines of one invoice (supersede, never delete), so the CSV's
   * now-complete lines are the sole contributors. Returns the number of lines closed.
   */
  public int supersedePdfOriginLines(String invoiceNumber) {
    return service.supersedePdfOriginLines(invoiceNumber);
  }
}
