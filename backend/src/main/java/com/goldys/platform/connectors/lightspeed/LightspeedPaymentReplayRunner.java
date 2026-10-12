package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: re-parse already-stored Lightspeed payments raw payloads into canonical rows, so
 * the bulk "all-payments" report stored before the {@code maxStringLength} fix is recovered without
 * re-fetching anything. Runs only when {@code app.lightspeed-payments-replay=true} and before the
 * projection seeder, so the read model sees the recovered rows.
 *
 * <p>Idempotent: a re-parsed row whose facts are unchanged is a no-op at the canonical layer. A
 * single unparseable stored payload is logged and skipped. Payloads are processed one raw record at
 * a time so the whole payments ledger (hundreds of MB of embedded CSV) is never materialised in
 * memory at once.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.lightspeed-payments-replay", havingValue = "true")
public class LightspeedPaymentReplayRunner implements ApplicationRunner {
  private final RawLedgerQuery rawLedger;
  private final LightspeedPaymentIngestService payments;

  public LightspeedPaymentReplayRunner(
      RawLedgerQuery rawLedger, LightspeedPaymentIngestService payments) {
    this.rawLedger = rawLedger;
    this.payments = payments;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (UUID id : rawLedger.rawRecordIdsForFetcher("lightspeed-payments")) {
      byte[] bytes = rawLedger.payloadBytes(id);
      if (bytes != null) {
        payments.backfill(new RawLedgerQuery.RawPayload(id, bytes));
      }
    }
  }
}
