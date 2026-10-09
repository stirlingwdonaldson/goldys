package com.goldys.platform.canonical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical invoice metadata, so modules outside this package can
 * resolve the CSV's PDF filename to the invoice number without touching the package-private
 * repository. Used by the PDF enrichment path to prefer the filename mapping (spec §7) over the
 * PDF's own text invoice number.
 */
@Service
public class CanonicalInvoiceQuery {
  private final CanonicalInvoiceRepository repository;

  public CanonicalInvoiceQuery(CanonicalInvoiceRepository repository) {
    this.repository = repository;
  }

  /** The current (non-superseded) invoice number for a PDF filename, or empty when unknown. */
  public Optional<String> invoiceNumberForPdfFilename(String pdfFilename) {
    if (pdfFilename == null) {
      return Optional.empty();
    }
    return repository.currentInvoiceNumberByPdfFilename(pdfFilename);
  }

  /** The current supplier name for every current invoice, keyed by invoice number. */
  public Map<String, String> currentSupplierNames() {
    Map<String, String> out = new LinkedHashMap<>();
    for (CanonicalInvoice invoice : repository.findAllCurrent()) {
      out.put(invoice.invoiceNumber(), invoice.supplierName());
    }
    return out;
  }

  /** All current invoice metadata, so modules outside this package can read invoices. */
  public List<InvoiceMetadataView> currentInvoices() {
    return repository.findAllCurrent().stream().map(CanonicalInvoiceQuery::toView).toList();
  }

  private static InvoiceMetadataView toView(CanonicalInvoice i) {
    return new InvoiceMetadataView(
        i.supplierName(), i.invoiceNumber(), i.invoiceDate(), i.totalAmount(),
        i.purchaseNumber(), i.pdfFilename());
  }
}
