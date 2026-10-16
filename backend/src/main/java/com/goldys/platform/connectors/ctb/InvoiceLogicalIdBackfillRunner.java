package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off re-key backfill: resolves the parent invoice reference ({@code invoice_logical_id}) for
 * current invoice lines ingested before that column existed, so the {@code invoice → invoice_line}
 * join resolves for legacy purchasing data. Runs only when {@code
 * app.invoice-logical-id-backfill=true}, before the projection seeder, and is idempotent — a line
 * that already has a parent id is not "current with a null parent" and is skipped, so a re-run
 * re-keys nothing. Orphan lines (no current header) and ambiguous invoice numbers (more than one
 * current invoice) are left null, an explicit unresolved relationship rather than a guess.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.invoice-logical-id-backfill", havingValue = "true")
public class InvoiceLogicalIdBackfillRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(InvoiceLogicalIdBackfillRunner.class);

  private final CanonicalInvoiceLineIngest lineIngest;

  public InvoiceLogicalIdBackfillRunner(CanonicalInvoiceLineIngest lineIngest) {
    this.lineIngest = lineIngest;
  }

  @Override
  public void run(ApplicationArguments args) {
    int migrated = lineIngest.backfillInvoiceLogicalId();
    log.info("invoice_logical_id backfill re-keyed {} legacy line(s)", migrated);
  }
}
