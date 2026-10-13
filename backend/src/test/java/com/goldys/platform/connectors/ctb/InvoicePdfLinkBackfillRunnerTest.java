package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.canonical.InvoicePdfLinkSource;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.RawLedgerQuery.RawPayload;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoicePdfLinkBackfillRunnerTest {

  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
  private final InvoicePdfExtractor parser = mock(InvoicePdfExtractor.class);
  private final CanonicalInvoiceQuery invoiceQuery = mock(CanonicalInvoiceQuery.class);
  private final InvoicePdfLinkService pdfLinkService = mock(InvoicePdfLinkService.class);

  @BeforeEach
  void everyInvoiceIsKnownUnlessATestSaysOtherwise() {
    when(invoiceQuery.isKnownInvoice(any())).thenReturn(true);
  }

  private InvoicePdfLinkBackfillRunner runner() {
    return new InvoicePdfLinkBackfillRunner(
        rawLedger, extractor, parser, invoiceQuery, pdfLinkService, 2);
  }

  @Test
  void linksAResolvablePdfBackToItsInvoice() {
    byte[] bytes = {1};
    UUID id = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(id, bytes)));
    when(extractor.extractText(bytes, "application/pdf")).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));

    runner().run(null);

    verify(pdfLinkService).link("INV-1", id, null, InvoicePdfLinkSource.BACKFILL);
  }

  @Test
  void skipsAnAlreadyLinkedPdfWithoutExtractingOrLinking() {
    byte[] bytes = {1};
    UUID id = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(id, bytes)));
    when(pdfLinkService.isLinked(id)).thenReturn(true);

    runner().run(null);

    verify(extractor, never()).extractText(any(), any());
    verify(pdfLinkService, never()).link(any(), any(), any(), any());
  }

  @Test
  void countsAnUnresolvablePdfWithoutLinking() {
    byte[] bytes = {1};
    UUID id = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(id, bytes)));
    when(extractor.extractText(bytes, "application/pdf")).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice(null, List.of()));

    runner().run(null);

    verify(pdfLinkService, never()).link(any(), any(), any(), any());
  }

  @Test
  void countsAnUnknownInvoicePdfWithoutLinking() {
    byte[] bytes = {1};
    UUID id = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(id, bytes)));
    when(extractor.extractText(bytes, "application/pdf")).thenReturn("text");
    when(parser.extract("text")).thenReturn(new PdfExtractedInvoice("INV-1", List.of()));
    when(invoiceQuery.isKnownInvoice("INV-1")).thenReturn(false);

    runner().run(null);

    verify(pdfLinkService, never()).link(eq("INV-1"), eq(id), isNull(), any());
  }

  @Test
  void aSingleUnreadablePdfDoesNotAbortThePass() {
    byte[] bad = {1};
    byte[] good = {2};
    UUID idBad = UUID.randomUUID();
    UUID idGood = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(idBad, bad), new RawPayload(idGood, good)));
    when(extractor.extractText(good, "application/pdf")).thenReturn("good text");
    when(parser.extract("good text")).thenReturn(new PdfExtractedInvoice("INV-2", List.of()));
    doThrow(new RuntimeException("unparseable"))
        .when(extractor)
        .extractText(bad, "application/pdf");

    runner().run(null);

    // The unreadable PDF is counted as unresolved, but the pass continues to the next one.
    verify(pdfLinkService).link("INV-2", idGood, null, InvoicePdfLinkSource.BACKFILL);
  }
}
