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
 * One-off replay: re-parse already-stored Lightspeed sale-items raw payloads into canonical rows,
 * so reports stored before the {@code maxStringLength} fix are recovered without re-fetching
 * anything. Runs only when {@code app.lightspeed-sale-items-replay=true} and before the projection
 * seeder, so the read model sees the recovered rows.
 *
 * <p>Idempotent: a re-parsed row whose facts are unchanged is a no-op at the canonical layer. A
 * single unparseable stored payload (e.g. a source-side truncated report) is logged and skipped.
 * Payloads are processed one raw record at a time so the whole sale-items ledger (hundreds of MB of
 * embedded CSVs) is never materialised in memory at once.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.lightspeed-sale-items-replay", havingValue = "true")
public class LightspeedSaleItemReplayRunner implements ApplicationRunner {
  private final RawLedgerQuery rawLedger;
  private final LightspeedSaleItemIngestService saleItems;

  public LightspeedSaleItemReplayRunner(
      RawLedgerQuery rawLedger, LightspeedSaleItemIngestService saleItems) {
    this.rawLedger = rawLedger;
    this.saleItems = saleItems;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (UUID id : rawLedger.rawRecordIdsForFetcher("lightspeed-sale-items")) {
      byte[] bytes = rawLedger.payloadBytes(id);
      if (bytes != null) {
        saleItems.backfill(new RawLedgerQuery.RawPayload(id, bytes));
      }
    }
  }
}
