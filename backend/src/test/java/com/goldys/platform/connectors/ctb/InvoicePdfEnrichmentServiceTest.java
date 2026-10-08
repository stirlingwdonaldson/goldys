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
import org.junit.jupiter.api.Test;

class InvoicePdfEnrichmentServiceTest {

  private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
  private final InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
  private final CanonicalInvoiceLineEnrichment enrichment =
      mock(CanonicalInvoiceLineEnrichment.class);
  private final CanonicalInvoiceQuery invoiceQuery = mock(CanonicalInvoiceQuery.class);
  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);

  private InvoicePdfEnrichmentService service() {
    return new InvoicePdfEnrichmentService(extractor, parser, enrichment, invoiceQuery, flags);
  }

  @Test
  void usesTheFilenameMappedInvoiceNumberAsTheJoinKey() {
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "PDF-OWN-NUMBER",
                List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null))));
    when(invoiceQuery.invoiceNumberForPdfFilename("bruno-1.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    service().enrich(new byte[] {1}, "bruno-1.pdf");

    verify(enrichment).enrich(eq("INV-1"), eq("CODE"), any(), any(), any(), any());
    verify(flags, never()).flag(any(), any(), any(), any(), anyString());
  }

  @Test
  void fallsBackToThePdfTextInvoiceNumberWhenFilenameDoesNotResolve() {
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "PDF-OWN-NUMBER",
                List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null))));
    when(invoiceQuery.invoiceNumberForPdfFilename("unknown.pdf")).thenReturn(Optional.empty());
    when(enrichment.enrich(eq("PDF-OWN-NUMBER"), eq("CODE"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    service().enrich(new byte[] {1}, "unknown.pdf");

    verify(enrichment).enrich(eq("PDF-OWN-NUMBER"), eq("CODE"), any(), any(), any(), any());
  }

  @Test
  void flagsUnparseableWhenNoLinesAreParsed() {
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("some text");
    when(parser.extract("some text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.empty());

    service().enrich(new byte[] {1}, "a.pdf");

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_UNPARSEABLE),
            eq("INV-1"),
            eq("a.pdf"),
            eq(null),
            anyString());
  }

  @Test
  void flagsPdfOnlyLineWhenNoCsvLineMatches() {
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-1",
                List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null))));
    when(invoiceQuery.invoiceNumberForPdfFilename("a.pdf")).thenReturn(Optional.of("INV-1"));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);

    service().enrich(new byte[] {1}, "a.pdf");

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_ONLY_LINE),
            eq("INV-1"),
            eq("a.pdf"),
            eq("CODE"),
            eq("desc"));
  }
}
