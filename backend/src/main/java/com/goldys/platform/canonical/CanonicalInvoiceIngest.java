package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical invoice metadata, so connectors can record parsed facts. */
@Service
public class CanonicalInvoiceIngest {
  private final CanonicalInvoiceService service;

  public CanonicalInvoiceIngest(CanonicalInvoiceService service) {
    this.service = service;
  }

  /**
   * Records the invoice metadata and returns its identity (logical id + supplier-scoped source
   * key), which line items use as their parent reference.
   */
  public InvoiceIdentity record(InvoiceInput input) {
    return service.record(input);
  }
}
