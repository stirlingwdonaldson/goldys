package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoicePdfEnrichmentServiceTest {

  private static final UUID PDF_RAW_ID = UUID.randomUUID();

  private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
  private final InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
  private final CanonicalInvoiceLineEnrichment enrichment =
      mock(CanonicalInvoiceLineEnrichment.class);
  private final CanonicalInvoiceQuery invoiceQuery = mock(CanonicalInvoiceQuery.class);
  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
  private final InvoiceGapRecoveryService gapRecovery = mock(InvoiceGapRecoveryService.class);

  @BeforeEach
  void everyInvoiceIsKnownUnlessATestSaysOtherwise() {
    when(invoiceQuery.isKnownInvoice(any())).thenReturn(true);
  }

  private InvoicePdfEnrichmentService service() {
    return service(true);
  }

  private InvoicePdfEnrichmentService service(boolean gapRecoveryEnabled) {
    return new InvoicePdfEnrichmentService(
        extractor, parser, enrichment, invoiceQuery, flags, gapRecovery, gapRecoveryEnabled);
  }

  @Test
  void usesTheFilenameMappedInvoiceNumberAsTheJoinKey() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("PDF-OWN-NUMBER", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("bruno-1.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    service().enrich(pdf, "bruno-1.pdf", PDF_RAW_ID);

    verify(enrichment).enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any());
    verify(flags, never()).flag(any(), any(), any(), any(), anyString());
    // Recovery runs for every enriched PDF (matched or not); a fully-matched PDF passes an empty
    // unmatched set so the invoice-level reconciliation still runs.
    verify(gapRecovery)
        .recover(eq("INV-1"), eq("bruno-1.pdf"), eq(pdf), eq(PDF_RAW_ID), eq(lines), eq(Set.of()));
  }

  @Test
  void fallsBackToThePdfTextInvoiceNumberWhenFilenameDoesNotResolve() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("PDF-OWN-NUMBER", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("unknown.pdf")).thenReturn(Optional.empty());
    when(enrichment.enrich(
            eq("PDF-OWN-NUMBER"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    service().enrich(pdf, "unknown.pdf", PDF_RAW_ID);

    verify(enrichment)
        .enrich(eq("PDF-OWN-NUMBER"), eq("CODE"), eq("desc"), any(), any(), any(), any());
    verify(gapRecovery)
        .recover(
            eq("PDF-OWN-NUMBER"),
            eq("unknown.pdf"),
            eq(pdf),
            eq(PDF_RAW_ID),
            eq(lines),
            eq(Set.of()));
  }

  @Test
  void flagsUnparseableWhenNoLinesAreParsed() {
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("some text");
    when(parser.extract("some text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.empty());

    service().enrich(new byte[] {1}, "a.pdf", PDF_RAW_ID);

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_UNPARSEABLE),
            eq("INV-1"),
            eq("a.pdf"),
            eq(null),
            anyString());
    // No lines to reconcile: recovery is not reached.
    verify(gapRecovery, never()).recover(any(), any(), any(), any(), any(), any());
  }

  @Test
  void fallsBackToTheStoredInvoiceFilenameForFlagIdentityWhenReplayedWithoutOne() {
    // During a replay the raw PDF record has no filename, so the flag must reuse the invoice's
    // stored pdf_filename or a resolved flag would be re-created under a different identity.
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", lines));
    when(invoiceQuery.currentPdfFilenameForInvoiceNumber("INV-1"))
        .thenReturn(Optional.of("stored.pdf"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);

    service().enrich(pdf, null, PDF_RAW_ID);

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_ONLY_LINE),
            eq("INV-1"),
            eq("stored.pdf"),
            eq("CODE"),
            eq("desc"));
    verify(gapRecovery)
        .recover(eq("INV-1"), eq("stored.pdf"), eq(pdf), eq(PDF_RAW_ID), eq(lines), eq(Set.of(0)));
  }

  @Test
  void skipsGapRecoveryWhenTheFeatureFlagIsOff() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    service(false).enrich(pdf, "a.pdf", PDF_RAW_ID);

    verify(enrichment).enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any());
    verify(gapRecovery, never()).recover(any(), any(), any(), any(), any(), any());
  }

  @Test
  void flagsPdfOnlyLineWhenNoCsvLineMatches() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);

    service().enrich(pdf, "a.pdf", PDF_RAW_ID);

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_ONLY_LINE),
            eq("INV-1"),
            eq("a.pdf"),
            eq("CODE"),
            eq("desc"));
    verify(gapRecovery)
        .recover(eq("INV-1"), eq("a.pdf"), eq(pdf), eq(PDF_RAW_ID), eq(lines), eq(Set.of(0)));
  }

  @Test
  void callsRecoveryWithTheIndexesOfEveryUnmatchedLine() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(
            new PdfExtractedLine("A", "d1", null, "KG", null, null, null, null),
            new PdfExtractedLine("B", "d2", null, "KG", null, null, null, null),
            new PdfExtractedLine("C", "d3", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("A"), any(), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);
    when(enrichment.enrich(eq("INV-1"), eq("B"), any(), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);
    when(enrichment.enrich(eq("INV-1"), eq("C"), any(), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);

    service().enrich(pdf, "a.pdf", PDF_RAW_ID);

    verify(gapRecovery)
        .recover(eq("INV-1"), eq("a.pdf"), eq(pdf), eq(PDF_RAW_ID), eq(lines), eq(Set.of(0, 2)));
  }

  @Test
  void flagsAnUnresolvedLivePdfOnceInsteadOfEveryLine() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(
            new PdfExtractedLine("A", "d1", null, "KG", null, null, null, null),
            new PdfExtractedLine("B", "d2", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("053202347", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("orphan.pdf")).thenReturn(Optional.empty());
    when(invoiceQuery.isKnownInvoice("053202347")).thenReturn(false);

    service().enrich(pdf, "orphan.pdf", PDF_RAW_ID);

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.AMBIGUOUS_PDF),
            eq("053202347"),
            eq("orphan.pdf"),
            eq(null),
            anyString());
    verify(flags, never())
        .flag(eq(InvoiceIngestFlagType.PDF_ONLY_LINE), any(), any(), any(), any());
    verify(enrichment, never()).enrich(any(), any(), any(), any(), any(), any(), any());
    verify(gapRecovery, never()).recover(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anUnresolvedReplayedPdfRaisesNoFlag() {
    // Replay has no filename, so an unresolved text number most likely means the parser misread a
    // PDF the live path already joined by filename. Flagging it would be a false alarm.
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("A", "d1", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice(null, lines));
    when(invoiceQuery.isKnownInvoice(null)).thenReturn(false);

    service().enrich(new byte[] {1}, null, PDF_RAW_ID);

    verify(flags, never()).flag(any(), any(), any(), any(), any());
    verify(enrichment, never()).enrich(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void flagsAnAmbiguousLineButDoesNotTreatItAsMissingFromTheCsv() {
    byte[] pdf = new byte[] {1};
    List<PdfExtractedLine> lines =
        List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null, null));
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", lines));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), eq("desc"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.AMBIGUOUS);

    service().enrich(pdf, "a.pdf", PDF_RAW_ID);

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_ONLY_LINE),
            eq("INV-1"),
            eq("a.pdf"),
            eq("CODE"),
            eq("desc"));
    verify(gapRecovery)
        .recover(eq("INV-1"), eq("a.pdf"), eq(pdf), eq(PDF_RAW_ID), eq(lines), eq(Set.of()));
  }
}
