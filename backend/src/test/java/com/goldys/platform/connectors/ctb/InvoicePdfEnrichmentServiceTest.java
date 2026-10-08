package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.EnrichmentResult;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePdfEnrichmentServiceTest {

  @Test
  void flagsUnparseableWhenNoLinesAreParsed() {
    DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
    CanonicalInvoiceLineEnrichment enrichment = mock(CanonicalInvoiceLineEnrichment.class);
    InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("some text");
    when(parser.extract("some text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));

    new InvoicePdfEnrichmentService(extractor, parser, enrichment, flags).enrich(new byte[] {1});

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_UNPARSEABLE),
            eq("INV-1"),
            isNull(),
            isNull(),
            anyString());
  }

  @Test
  void flagsPdfOnlyLineWhenNoCsvLineMatches() {
    DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
    CanonicalInvoiceLineEnrichment enrichment = mock(CanonicalInvoiceLineEnrichment.class);
    InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-1",
                List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null))));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.NO_MATCH);

    new InvoicePdfEnrichmentService(extractor, parser, enrichment, flags).enrich(new byte[] {1});

    verify(flags)
        .flag(
            eq(InvoiceIngestFlagType.PDF_ONLY_LINE), eq("INV-1"), isNull(), eq("CODE"), eq("desc"));
  }

  @Test
  void doesNotFlagWhenTheLineEnriches() {
    DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
    CanonicalInvoiceLineEnrichment enrichment = mock(CanonicalInvoiceLineEnrichment.class);
    InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
    when(extractor.extractText(any(), eq("application/pdf"))).thenReturn("text");
    when(parser.extract("text"))
        .thenReturn(
            new PdfExtractedInvoice(
                "INV-1",
                List.of(new PdfExtractedLine("CODE", "desc", null, "KG", null, null, null))));
    when(enrichment.enrich(eq("INV-1"), eq("CODE"), any(), any(), any(), any()))
        .thenReturn(EnrichmentResult.ENRICHED);

    new InvoicePdfEnrichmentService(extractor, parser, enrichment, flags).enrich(new byte[] {1});

    verify(flags, never()).flag(any(), any(), any(), any(), anyString());
  }
}
