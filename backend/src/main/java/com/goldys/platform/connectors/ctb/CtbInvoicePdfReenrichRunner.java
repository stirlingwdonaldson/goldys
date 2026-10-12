package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService.OpenFlag;
import com.goldys.platform.ingestion.RawLedgerQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: re-runs PDF enrichment over the CTB invoice PDFs already stored in the raw
 * ledger, then retires the {@code PDF_ONLY_LINE} flags the improved matcher now resolves. Recovers
 * lines that the old exact-match enrichment left un-enriched (and the false anomalies it recorded)
 * without re-fetching anything. Runs only when {@code app.ctb-invoice-reenrich=true}, before the
 * projection seeder, so the inventory projection sees the re-enriched rows.
 *
 * <p>Like {@code LightspeedRawBackfillRunner}, it is enabled explicitly per deploy and idempotent:
 * re-enrichment writes no new version when the fields already match, and {@code resolve} is a no-op
 * for an already-resolved flag. A single unreadable PDF is logged and skipped rather than aborting
 * the pass.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.ctb-invoice-reenrich", havingValue = "true")
public class CtbInvoicePdfReenrichRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CtbInvoicePdfReenrichRunner.class);

  private final RawLedgerQuery rawLedger;
  private final InvoicePdfEnrichmentService pdfEnrichment;
  private final CanonicalInvoiceLineEnrichment lineEnrichment;
  private final InvoiceIngestFlagService flags;

  public CtbInvoicePdfReenrichRunner(
      RawLedgerQuery rawLedger,
      InvoicePdfEnrichmentService pdfEnrichment,
      CanonicalInvoiceLineEnrichment lineEnrichment,
      InvoiceIngestFlagService flags) {
    this.rawLedger = rawLedger;
    this.pdfEnrichment = pdfEnrichment;
    this.lineEnrichment = lineEnrichment;
    this.flags = flags;
  }

  @Override
  public void run(ApplicationArguments args) {
    reEnrichStoredPdfs();
    retireResolvedFlags();
  }

  private void reEnrichStoredPdfs() {
    int replayed = 0;
    for (RawLedgerQuery.RawPayload pdf : rawLedger.rawPayloadsForSource("CTB", "application/pdf")) {
      try {
        // No filename: the raw PDF record does not retain it, so enrichment targets the invoice via
        // the number parsed from the PDF text (and reuses the invoice's stored filename for flags).
        pdfEnrichment.enrich(pdf.bytes(), null, pdf.id());
        replayed++;
      } catch (RuntimeException e) {
        log.warn("CTB invoice PDF replay failed for raw record {}", pdf.id(), e);
      }
    }
    log.info("CTB invoice PDF replay re-enriched {} stored PDFs", replayed);
  }

  private void retireResolvedFlags() {
    int resolved = 0;
    for (OpenFlag flag : flags.openPdfOnlyLineFlags()) {
      if (lineEnrichment.hasMatch(flag.invoiceNumber(), flag.stockCode(), flag.detail())) {
        flags.resolve(flag.id());
        resolved++;
      }
    }
    log.info("CTB invoice PDF replay resolved {} previously flagged lines", resolved);
  }
}
