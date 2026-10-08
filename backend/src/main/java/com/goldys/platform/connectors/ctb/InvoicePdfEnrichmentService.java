package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import org.springframework.stereotype.Service;

/**
 * PDF enrichment pipeline: extract text, parse lines, and enrich the matching canonical lines. The
 * PDF's filename is the authoritative join key (spec §7): it resolves to the invoice number via the
 * CSV's persisted {@code pdf_filename}, falling back to the PDF's own text invoice number only when
 * the filename does not resolve. Anomalies are surfaced as {@link InvoiceIngestFlagType} flags
 * rather than silently no-op'ing.
 */
@Service
public class InvoicePdfEnrichmentService {
  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceLineEnrichment enrichment;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final InvoiceIngestFlagService flags;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment,
      CanonicalInvoiceQuery invoiceQuery,
      InvoiceIngestFlagService flags) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
    this.invoiceQuery = invoiceQuery;
    this.flags = flags;
  }

  public void enrich(byte[] pdf, String pdfFilename) {
    String text = extractor.extractText(pdf, "application/pdf");
    PdfExtractedInvoice parsed = parser.extract(text);
    String invoiceNumber =
        invoiceQuery.invoiceNumberForPdfFilename(pdfFilename).orElse(parsed.invoiceNumber());
    if (parsed.lines().isEmpty()) {
      flags.flag(
          InvoiceIngestFlagType.PDF_UNPARSEABLE,
          invoiceNumber,
          pdfFilename,
          null,
          (text == null || text.isBlank()) ? "no extractable text" : "no lines parsed");
      return;
    }
    for (PdfExtractedLine line : parsed.lines()) {
      EnrichmentResult result =
          enrichment.enrich(
              invoiceNumber,
              line.stockCode(),
              line.description(),
              line.uom(),
              line.unitQuantity(),
              line.packSize(),
              line.wetAmount());
      if (result == EnrichmentResult.NO_MATCH) {
        flags.flag(
            InvoiceIngestFlagType.PDF_ONLY_LINE,
            invoiceNumber,
            pdfFilename,
            line.stockCode(),
            line.description());
      }
    }
  }
}
