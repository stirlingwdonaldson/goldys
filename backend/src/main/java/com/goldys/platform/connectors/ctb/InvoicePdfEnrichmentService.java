package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
  private final InvoiceGapRecoveryService gapRecovery;
  private final boolean gapRecoveryEnabled;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment,
      CanonicalInvoiceQuery invoiceQuery,
      InvoiceIngestFlagService flags,
      InvoiceGapRecoveryService gapRecovery,
      @Value("${app.ctb-invoice-gap-recovery:false}") boolean gapRecoveryEnabled) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
    this.invoiceQuery = invoiceQuery;
    this.flags = flags;
    this.gapRecovery = gapRecovery;
    this.gapRecoveryEnabled = gapRecoveryEnabled;
  }

  public void enrich(byte[] pdf, String pdfFilename, UUID pdfRawRecordId) {
    String text = extractor.extractText(pdf, "application/pdf");
    PdfExtractedInvoice parsed = parser.extract(text);
    String invoiceNumber =
        invoiceQuery.invoiceNumberForPdfFilename(pdfFilename).orElse(parsed.invoiceNumber());
    // A replayed PDF has no filename on its raw record; fall back to the invoice's stored
    // pdf_filename so flag identities stay stable across the live and replay paths.
    String flagFilename =
        pdfFilename != null
            ? pdfFilename
            : invoiceQuery.currentPdfFilenameForInvoiceNumber(invoiceNumber).orElse(null);
    if (parsed.lines().isEmpty()) {
      flags.flag(
          InvoiceIngestFlagType.PDF_UNPARSEABLE,
          invoiceNumber,
          flagFilename,
          null,
          (text == null || text.isBlank()) ? "no extractable text" : "no lines parsed");
      return;
    }
    Set<Integer> unmatchedIndexes = new HashSet<>();
    for (int i = 0; i < parsed.lines().size(); i++) {
      PdfExtractedLine line = parsed.lines().get(i);
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
        unmatchedIndexes.add(i);
        flags.flag(
            InvoiceIngestFlagType.PDF_ONLY_LINE,
            invoiceNumber,
            flagFilename,
            line.stockCode(),
            line.description());
      }
    }
    // Gap recovery runs for every enriched PDF (matched or not): the reconciliation is over the
    // full PDF line sum, so a CSV-short invoice is caught even when some of its lines matched. It
    // is off by default (app.ctb-invoice-gap-recovery=false) and must be enabled per deploy before
    // any PDF-derived lines are created.
    if (gapRecoveryEnabled) {
      gapRecovery.recover(
          invoiceNumber, flagFilename, pdf, pdfRawRecordId, parsed.lines(), unmatchedIndexes);
    }
  }
}
