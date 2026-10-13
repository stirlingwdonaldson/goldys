package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceQuery;
import com.goldys.platform.canonical.InvoicePdfLinkService;
import com.goldys.platform.canonical.InvoicePdfLinkSource;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.DocumentTextExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off backfill: re-derives the invoice→PDF link for the CTB invoice PDFs already stored in the
 * raw ledger by parsing each PDF's own text for its invoice number, then recording a {@link
 * InvoicePdfLinkSource#BACKFILL} link when that number resolves to an invoice the CTB export knows.
 * Recovers links for the historical PDFs ingested before the link was captured on the live path,
 * without re-fetching anything. Runs only when {@code app.ctb-invoice-pdf-link-backfill=true},
 * before the projection seeder.
 *
 * <p>Idempotent: an already-linked PDF ({@code isLinked}) is skipped without re-extracting, and
 * {@link InvoicePdfLinkService#link} is a no-op for a raw record that already has a link. A single
 * unreadable PDF is counted as unresolved and logged rather than aborting the pass.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.ctb-invoice-pdf-link-backfill", havingValue = "true")
public class InvoicePdfLinkBackfillRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(InvoicePdfLinkBackfillRunner.class);

  private final RawLedgerQuery rawLedger;
  private final DocumentTextExtractor extractor;
  private final InvoicePdfExtractor parser;
  private final CanonicalInvoiceQuery invoiceQuery;
  private final InvoicePdfLinkService pdfLinkService;
  private final int concurrency;

  public InvoicePdfLinkBackfillRunner(
      RawLedgerQuery rawLedger,
      DocumentTextExtractor extractor,
      InvoicePdfExtractor parser,
      CanonicalInvoiceQuery invoiceQuery,
      InvoicePdfLinkService pdfLinkService,
      @Value("${app.ctb-invoice-replay-concurrency:8}") int concurrency) {
    this.rawLedger = rawLedger;
    this.extractor = extractor;
    this.parser = parser;
    this.invoiceQuery = invoiceQuery;
    this.pdfLinkService = pdfLinkService;
    this.concurrency = concurrency;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<RawLedgerQuery.RawPayload> pdfs = rawLedger.rawPayloadsForSource("CTB", "application/pdf");
    long started = System.currentTimeMillis();
    // Linking is per-PDF independent, so PDFs can be processed concurrently with no shared state.
    // A fixed pool bounds the combined PDFBox/LLM work, mirroring the re-enrich runner.
    ExecutorService pool = Executors.newFixedThreadPool(concurrency);
    try {
      AtomicInteger linked = new AtomicInteger();
      AtomicInteger alreadyLinked = new AtomicInteger();
      AtomicInteger unresolved = new AtomicInteger();
      List<CompletableFuture<Void>> futures = new ArrayList<>(pdfs.size());
      for (RawLedgerQuery.RawPayload pdf : pdfs) {
        futures.add(
            CompletableFuture.runAsync(
                () -> {
                  try {
                    if (pdfLinkService.isLinked(pdf.id())) {
                      alreadyLinked.incrementAndGet();
                      return;
                    }
                    String text = extractor.extractText(pdf.bytes(), "application/pdf");
                    String number = parser.extract(text).invoiceNumber();
                    if (number != null && invoiceQuery.isKnownInvoice(number)) {
                      // No filename: the raw PDF record does not retain it, so the link records
                      // only
                      // the invoice number and raw record id.
                      pdfLinkService.link(number, pdf.id(), null, InvoicePdfLinkSource.BACKFILL);
                      linked.incrementAndGet();
                    } else {
                      unresolved.incrementAndGet();
                    }
                  } catch (RuntimeException e) {
                    unresolved.incrementAndGet();
                    log.warn("CTB invoice PDF link backfill failed for raw record {}", pdf.id(), e);
                  }
                },
                pool));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      long elapsed = System.currentTimeMillis() - started;
      log.info(
          "CTB invoice PDF link backfill linked {} of {} stored PDFs ({} already linked, "
              + "{} unresolved, {} ms)",
          linked.get(),
          pdfs.size(),
          alreadyLinked.get(),
          unresolved.get(),
          elapsed);
    } finally {
      pool.shutdown();
    }
  }
}
