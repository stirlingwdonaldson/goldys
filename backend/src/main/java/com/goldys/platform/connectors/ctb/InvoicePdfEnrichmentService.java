package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import org.springframework.stereotype.Service;

/**
 * PDF enrichment pipeline: extract text, parse lines, and enrich the matching canonical lines. The
 * invoice number comes from the PDF's own text (the parser's known layout reads it reliably); the
 * CSV-filename mapping is a later hardening (spec §7, see Deferred).
 */
@Service
public class InvoicePdfEnrichmentService {
  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceLineEnrichment enrichment;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
  }

  public void enrich(byte[] pdf) {
    String text = extractor.extractText(pdf, "application/pdf");
    PdfExtractedInvoice parsed = parser.extract(text);
    for (PdfExtractedLine line : parsed.lines()) {
      enrichment.enrich(parsed.invoiceNumber(), line);
    }
  }
}
