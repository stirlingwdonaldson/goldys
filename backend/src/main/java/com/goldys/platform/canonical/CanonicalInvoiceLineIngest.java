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
}
