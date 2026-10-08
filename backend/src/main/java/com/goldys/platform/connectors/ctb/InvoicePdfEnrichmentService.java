package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import org.springframework.stereotype.Service;

/**
 * PDF enrichment pipeline: extract text, parse lines, and enrich the matching canonical lines.
 * Anomalies are surfaced as first-class {@link InvoiceIngestFlagType} flags rather than silently
 * no-op'ing — an unparseable PDF and a PDF-only line (no CSV counterpart) are both recorded.
 */
@Service
public class InvoicePdfEnrichmentService {
  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceLineEnrichment enrichment;
  private final InvoiceIngestFlagService flags;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment,
      InvoiceIngestFlagService flags) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
    this.flags = flags;
  }

  public void enrich(byte[] pdf) {
    String text = extractor.extractText(pdf, "application/pdf");
    PdfExtractedInvoice parsed = parser.extract(text);
    if (parsed.lines().isEmpty()) {
      flags.flag(
          InvoiceIngestFlagType.PDF_UNPARSEABLE,
          parsed.invoiceNumber(),
          null,
          null,
          (text == null || text.isBlank()) ? "no extractable text" : "no lines parsed");
      return;
    }
    for (PdfExtractedLine line : parsed.lines()) {
      EnrichmentResult result =
          enrichment.enrich(
              parsed.invoiceNumber(),
              line.stockCode(),
              line.uom(),
              line.unitQuantity(),
              line.packSize(),
              line.wetAmount());
      if (result == EnrichmentResult.NO_MATCH) {
        flags.flag(
            InvoiceIngestFlagType.PDF_ONLY_LINE,
            parsed.invoiceNumber(),
            null,
            line.stockCode(),
            line.description());
      }
    }
  }
}
