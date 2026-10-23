package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public write facade for canonical invoice metadata, so connectors can record parsed facts. */
@Service
public class CanonicalInvoiceIngest {
  private final CanonicalInvoiceService service;

  private final CanonicalInvoiceLineService lines;

  public CanonicalInvoiceIngest(
      CanonicalInvoiceService service, CanonicalInvoiceLineService lines) {
    this.service = service;
    this.lines = lines;
  }

  /**
   * Records the invoice metadata and returns its identity (logical id + supplier-scoped source
   * key), which line items use as their parent reference.
   */
  public InvoiceIdentity record(InvoiceInput input) {
    return service.record(input);
  }

  /** Header and lines commit together; an ambiguous revision leaves the raw delivery intact. */
  @Transactional
  public InvoiceIdentity recordCsv(
      InvoiceInput header, List<InvoiceLineInput> inputs, boolean completeSnapshot) {
    InvoiceIdentity identity = service.record(header);
    lines.recordCsvInvoice(
        identity,
        header.invoiceNumber(),
        inputs.stream().map(input -> input.forInvoice(identity)).toList(),
        completeSnapshot);
    return identity;
  }
}
