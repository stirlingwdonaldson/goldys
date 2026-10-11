package com.goldys.platform.connectors.ctb;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService.OpenFlag;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.RawLedgerQuery.RawPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbInvoicePdfReenrichRunnerTest {

  private final RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
  private final InvoicePdfEnrichmentService pdfEnrichment = mock(InvoicePdfEnrichmentService.class);
  private final CanonicalInvoiceLineEnrichment lineEnrichment =
      mock(CanonicalInvoiceLineEnrichment.class);
  private final InvoiceIngestFlagService flags = mock(InvoiceIngestFlagService.class);

  private CtbInvoicePdfReenrichRunner runner() {
    return new CtbInvoicePdfReenrichRunner(rawLedger, pdfEnrichment, lineEnrichment, flags);
  }

  @Test
  void reEnrichesEveryStoredPdf() {
    byte[] a = {1};
    byte[] b = {2};
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(
            List.of(new RawPayload(UUID.randomUUID(), a), new RawPayload(UUID.randomUUID(), b)));
    when(flags.openPdfOnlyLineFlags()).thenReturn(List.of());

    runner().run(null);

    verify(pdfEnrichment).enrich(a, null);
    verify(pdfEnrichment).enrich(b, null);
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
                new OpenFlag(genuine, "INV-2", null, "COMMENT")));
    when(lineEnrichment.hasMatch("INV-1", "LIMES", "LIMES TRAY")).thenReturn(true);
    when(lineEnrichment.hasMatch("INV-2", null, "COMMENT")).thenReturn(false);

    runner().run(null);

    verify(flags).resolve(resolved);
    verify(flags, never()).resolve(genuine);
  }

  @Test
  void aSingleUnreadablePdfDoesNotAbortThePass() {
    byte[] bad = {1};
    byte[] good = {2};
    when(rawLedger.rawPayloadsForSource("CTB", "application/pdf"))
        .thenReturn(
            List.of(
                new RawPayload(UUID.randomUUID(), bad), new RawPayload(UUID.randomUUID(), good)));
    when(flags.openPdfOnlyLineFlags()).thenReturn(List.of());
    doThrow(new RuntimeException("unparseable")).when(pdfEnrichment).enrich(bad, null);

    runner().run(null);

    // The unreadable PDF is skipped, but the pass continues to the next one.
    verify(pdfEnrichment).enrich(bad, null);
    verify(pdfEnrichment).enrich(good, null);
  }
}
