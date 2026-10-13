package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.canonical.InvoicePdfLinkSource;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(InvoicePdfEnrichmentService.class);

  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceLineEnrichment enrichment;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final InvoiceIngestFlagService flags;
  private final InvoiceGapRecoveryService gapRecovery;
  private final InvoicePdfLinkService pdfLinkService;
  private final boolean gapRecoveryEnabled;

  public InvoicePdfEnrichmentService(
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceLineEnrichment enrichment,
      CanonicalInvoiceQuery invoiceQuery,
      InvoiceIngestFlagService flags,
      InvoiceGapRecoveryService gapRecovery,
      InvoicePdfLinkService pdfLinkService,
      @Value("${app.ctb-invoice-gap-recovery:false}") boolean gapRecoveryEnabled) {
    this.extractor = extractor;
    this.parser = parser;
    this.enrichment = enrichment;
    this.invoiceQuery = invoiceQuery;
    this.flags = flags;
    this.gapRecovery = gapRecovery;
    this.pdfLinkService = pdfLinkService;
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
    // Link the PDF to its invoice as soon as it resolves, before extraction is judged: the viewer
    // needs the invoice→PDF pointer even when extraction later fails or parses no usable lines. The
    // result is reused below so the known-invoice check runs once.
    boolean known = invoiceQuery.isKnownInvoice(invoiceNumber);
    if (known) {
      pdfLinkService.link(
          invoiceNumber, pdfRawRecordId, pdfFilename, InvoicePdfLinkSource.ENRICHMENT);
    }
    if (parsed.extractionError() != null) {
      flags.flag(
          InvoiceIngestFlagType.LLM_EXTRACTION_FAILED,
          invoiceNumber,
          flagFilename,
          null,
          parsed.extractionError());
      return;
    }
    if (parsed.lines().isEmpty()) {
      flags.flag(
          InvoiceIngestFlagType.PDF_UNPARSEABLE,
          invoiceNumber,
          flagFilename,
          null,
          (text == null || text.isBlank()) ? "no extractable text" : "no lines parsed");
      return;
    }
    if (!known) {
      flagUnresolvedInvoice(parsed.invoiceNumber(), pdfFilename);
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
      }
      // AMBIGUOUS (several CSV lines tie) is flagged too, so a skipped line is never silent. It is
      // not passed to gap recovery: the CSV most likely does carry the line, just not uniquely.
      if (result == EnrichmentResult.NO_MATCH || result == EnrichmentResult.AMBIGUOUS) {
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

  /**
   * The PDF resolves to no invoice the CTB export knows (neither its filename nor its own invoice
   * number match). Matching its lines would flag every one of them as missing from the CSV, which
   * is what produced per-line flags under labels like "Tax Invoice No". Instead:
   *
   * <ul>
   *   <li>Live drop (filename known): one {@code AMBIGUOUS_PDF} flag for the whole PDF.
   *   <li>Replay (no filename): nothing is flagged. The raw PDF no longer carries its filename, so
   *       an unresolved text number here usually means the parser misread a PDF the live path had
   *       already joined by filename; a flag would be a false alarm.
   * </ul>
   */
  private void flagUnresolvedInvoice(String parsedInvoiceNumber, String pdfFilename) {
    if (pdfFilename == null) {
      log.info(
          "Replayed invoice PDF did not resolve to a known invoice (parsed number: {}); skipped",
          parsedInvoiceNumber);
      return;
    }
    flags.flag(
        InvoiceIngestFlagType.AMBIGUOUS_PDF,
        parsedInvoiceNumber,
        pdfFilename,
        null,
        "PDF not linked to any invoice in the CTB export (PDF invoice number: "
            + (parsedInvoiceNumber == null ? "unreadable" : parsedInvoiceNumber)
            + ")");
  }
}
