package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineEnrichment;
import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.connectors.ctb.InvoiceIngestFlagService.OpenFlag;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * <p>Besides flags the matcher now resolves, the pass also retires two kinds of false alarm the
 * extractor now filters out: annotation rows ({@code "(2 BOXES)"}, {@code "COMMENT"}) and lines
 * flagged against an invoice number the CTB export does not know (e.g. a header label like {@code
 * "Tax Invoice No"} read as the number). Neither could ever be matched, so they never belonged in
 * the work queue.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.ctb-invoice-reenrich", havingValue = "true")
public class CtbInvoicePdfReenrichRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CtbInvoicePdfReenrichRunner.class);

  /** Number of PDFs enriched concurrently. Bounds the combined PDFBox + LLM work. */
  private static final int CONCURRENCY = 8;

  private final RawLedgerQuery rawLedger;
  private final InvoicePdfEnrichmentService pdfEnrichment;
  private final CanonicalInvoiceLineEnrichment lineEnrichment;
  private final InvoiceIngestFlagService flags;
  private final CanonicalInvoiceQuery invoiceQuery;

  public CtbInvoicePdfReenrichRunner(
      RawLedgerQuery rawLedger,
      InvoicePdfEnrichmentService pdfEnrichment,
      CanonicalInvoiceLineEnrichment lineEnrichment,
      InvoiceIngestFlagService flags,
      CanonicalInvoiceQuery invoiceQuery) {
    this.rawLedger = rawLedger;
    this.pdfEnrichment = pdfEnrichment;
    this.lineEnrichment = lineEnrichment;
    this.flags = flags;
    this.invoiceQuery = invoiceQuery;
  }

  @Override
  public void run(ApplicationArguments args) {
    reEnrichStoredPdfs();
    retireResolvedFlags();
  }

  private void reEnrichStoredPdfs() {
    List<RawLedgerQuery.RawPayload> pdfs = rawLedger.rawPayloadsForSource("CTB", "application/pdf");
    long started = System.currentTimeMillis();
    // Enrichment is per-PDF independent: each line enrichment is its own transaction (a SELECT ...
    // FOR UPDATE on the invoice's lines), so PDFs can be processed concurrently with no shared
    // state. A fixed pool bounds the combined PDFBox/LLM work.
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
    try {
      AtomicInteger replayed = new AtomicInteger();
      List<CompletableFuture<Void>> futures = new ArrayList<>(pdfs.size());
      for (RawLedgerQuery.RawPayload pdf : pdfs) {
        futures.add(CompletableFuture.runAsync(() -> enrichPdf(pdf, replayed), pool));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      log.info(
          "CTB invoice PDF replay re-enriched {} of {} stored PDFs ({} ms)",
          replayed.get(),
          pdfs.size(),
          System.currentTimeMillis() - started);
    } finally {
      pool.shutdown();
    }
  }

  private void enrichPdf(RawLedgerQuery.RawPayload pdf, AtomicInteger replayed) {
    try {
      // No filename: the raw PDF record does not retain it, so enrichment targets the invoice via
      // the number parsed from the PDF text (and reuses the invoice's stored filename for flags).
      pdfEnrichment.enrich(pdf.bytes(), null, pdf.id());
      replayed.incrementAndGet();
    } catch (RuntimeException e) {
      log.warn("CTB invoice PDF replay failed for raw record {}", pdf.id(), e);
    }
  }

  private void retireResolvedFlags() {
    int matched = 0;
    int annotations = 0;
    int unknownInvoice = 0;
    for (OpenFlag flag : flags.openPdfOnlyLineFlags()) {
      if (InvoicePdfSanitizer.isAnnotationText(flag.stockCode(), flag.detail())) {
        flags.resolve(flag.id());
        annotations++;
      } else if (!invoiceQuery.isKnownInvoice(flag.invoiceNumber())) {
        flags.resolve(flag.id());
        unknownInvoice++;
      } else if (lineEnrichment.hasMatch(flag.invoiceNumber(), flag.stockCode(), flag.detail())) {
        flags.resolve(flag.id());
        matched++;
      }
    }
    log.info(
        "CTB invoice PDF replay resolved {} flagged lines: {} now matched, {} annotations, "
            + "{} against an invoice number the CTB export does not know",
        matched + annotations + unknownInvoice,
        matched,
        annotations,
        unknownInvoice);
  }
}
