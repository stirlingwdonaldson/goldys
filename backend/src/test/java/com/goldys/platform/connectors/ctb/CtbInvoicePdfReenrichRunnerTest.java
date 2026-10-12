package com.goldys.platform.connectors.ctb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService.OpenFlag;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.RawLedgerQuery.RawPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CtbInvoicePdfReenrichRunnerTest {

  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final InvoicePdfEnrichmentService pdfEnrichment = mock(InvoicePdfEnrichmentService.class);
  private final CanonicalInvoiceLineEnrichment lineEnrichment =
      mock(CanonicalInvoiceLineEnrichment.class);
  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);
  private final CanonicalInvoiceQuery invoiceQuery = mock(CanonicalInvoiceQuery.class);

  @BeforeEach
  void everyInvoiceIsKnownUnlessATestSaysOtherwise() {
    when(invoiceQuery.isKnownInvoice(any())).thenReturn(true);
  }

  private CtbInvoicePdfReenrichRunner runner() {
    return new CtbInvoicePdfReenrichRunner(
        rawLedger, pdfEnrichment, lineEnrichment, flags, invoiceQuery, 2);
  }

  @Test
  void reEnrichesEveryStoredPdf() {
    byte[] a = {1};
    byte[] b = {2};
    UUID idA = UUID.randomUUID();
    UUID idB = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(idA, a), new RawPayload(idB, b)));
    when(flags.openPdfOnlyLineFlags()).thenReturn(List.of());

    runner().run(null);

    verify(pdfEnrichment).enrich(a, null, idA);
    verify(pdfEnrichment).enrich(b, null, idB);
  }

  @Test
  void retiresOnlyTheFlagsTheMatcherNowResolves() {
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf")).thenReturn(List.of());
    UUID resolved = UUID.randomUUID();
    UUID genuine = UUID.randomUUID();
    when(flags.openPdfOnlyLineFlags())
        .thenReturn(
            List.of(
                new OpenFlag(resolved, "INV-1", "LIMES", "LIMES TRAY"),
                new OpenFlag(genuine, "INV-2", null, "LUZERNE ROUND BOWL")));
    when(lineEnrichment.hasMatch("INV-1", "LIMES", "LIMES TRAY")).thenReturn(true);
    when(lineEnrichment.hasMatch("INV-2", null, "LUZERNE ROUND BOWL")).thenReturn(false);

    runner().run(null);

    verify(flags).resolve(resolved);
    verify(flags, never()).resolve(genuine);
  }

  @Test
  void aSingleUnreadablePdfDoesNotAbortThePass() {
    byte[] bad = {1};
    byte[] good = {2};
    UUID idBad = UUID.randomUUID();
    UUID idGood = UUID.randomUUID();
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(List.of(new RawPayload(idBad, bad), new RawPayload(idGood, good)));
    when(flags.openPdfOnlyLineFlags()).thenReturn(List.of());
    doThrow(new RuntimeException("unparseable")).when(pdfEnrichment).enrich(bad, null, idBad);

    runner().run(null);

    // The unreadable PDF is skipped, but the pass continues to the next one.
    verify(pdfEnrichment).enrich(bad, null, idBad);
    verify(pdfEnrichment).enrich(good, null, idGood);
  }

  @Test
  void retiresAnnotationFlagsAndFlagsAgainstAnUnknownInvoice() {
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf")).thenReturn(List.of());
    UUID boxes = UUID.randomUUID();
    UUID comment = UUID.randomUUID();
    UUID label = UUID.randomUUID();
    UUID genuine = UUID.randomUUID();
    when(flags.openPdfOnlyLineFlags())
        .thenReturn(
            List.of(
                new OpenFlag(boxes, "351289", null, "(2 BOXES)"),
                new OpenFlag(comment, "354939", null, "COMMENT"),
                new OpenFlag(label, "Tax Invoice No", null, "XLFRY"),
                new OpenFlag(genuine, "758262", "B12BG", "Brush Grill Medium")));
    when(invoiceQuery.isKnownInvoice("Tax Invoice No")).thenReturn(false);
    when(lineEnrichment.hasMatch("758262", "B12BG", "Brush Grill Medium")).thenReturn(false);

    runner().run(null);

    verify(flags).resolve(boxes);
    verify(flags).resolve(comment);
    verify(flags).resolve(label);
    verify(flags, never()).resolve(genuine);
  }
}
