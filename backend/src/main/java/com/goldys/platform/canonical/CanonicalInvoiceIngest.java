package com.goldys.platform.canonical;

import org.springframework.stereotype.Service;

/** Public write facade for canonical invoice metadata, so connectors can record parsed facts. */
@Service
public class CanonicalInvoiceIngest {
  private final CanonicalInvoiceService service;

  public CanonicalInvoiceIngest(CanonicalInvoiceService service) {
    this.service = service;
  }

  public void record(InvoiceInput input) {
    service.record(input);
  }
}
