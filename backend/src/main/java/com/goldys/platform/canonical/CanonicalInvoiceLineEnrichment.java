package com.goldys.platform.canonical;

import com.goldys.platform.connectors.ctb.PdfExtractedLine;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies PDF enrichment to a canonical line: finds the current line by (invoice_number,
 * stock_code) and supersedes it with the PDF's uom/unit_quantity/pack_size/wet_amount, keeping the
 * CSV's quantity/unit_cost/line_total (CSV is authoritative). A no-op when no line matches.
 */
@Service
public class CanonicalInvoiceLineEnrichment {
  private final CanonicalInvoiceLineRepository repository;
  private final CanonicalInvoiceLineService service;

  public CanonicalInvoiceLineEnrichment(
      CanonicalInvoiceLineRepository repository, CanonicalInvoiceLineService service) {
    this.repository = repository;
    this.service = service;
  }

  @Transactional
  public void enrich(String invoiceNumber, PdfExtractedLine line) {
    Optional<CanonicalInvoiceLine> current =
        repository.findCurrentByInvoiceNumberAndStockCode(invoiceNumber, line.stockCode());
    if (current.isEmpty()) {
      return;
    }
    CanonicalInvoiceLine l = current.get();
    service.record(
        new InvoiceLineInput(
            l.sourceSystem(),
            l.sourceRecordRef(),
            l.invoiceNumber(),
            l.invoiceDate(),
            l.productNameKey(),
            l.stockCode(),
            l.quantity(),
            l.unitCost(),
            l.lineTotal(),
            l.category(),
            line.uom(),
            line.unitQuantity(),
            line.packSize(),
            line.wetAmount(),
            l.rawRecordId()));
  }
}
