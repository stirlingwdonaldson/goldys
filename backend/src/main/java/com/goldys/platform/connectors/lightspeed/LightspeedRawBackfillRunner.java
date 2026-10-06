package com.goldys.platform.connectors.lightspeed;

import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off backfill: re-parse already-stored Lightspeed raw payloads into canonical rows, so data
 * ingested before the parser fix is recovered. Runs only when {@code app.lightspeed-backfill=true}
 * and before the projection seeder, so the reconciliation projector sees the backfilled rows.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.lightspeed-backfill", havingValue = "true")
public class LightspeedRawBackfillRunner implements ApplicationRunner {
  private final RawLedgerQuery rawLedger;
  private final LightspeedIngestService lightspeed;

  public LightspeedRawBackfillRunner(RawLedgerQuery rawLedger, LightspeedIngestService lightspeed) {
    this.rawLedger = rawLedger;
    this.lightspeed = lightspeed;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<RawLedgerQuery.RawPayload> payloads = rawLedger.rawPayloadsForSource("LIGHTSPEED");
    if (!payloads.isEmpty()) {
      lightspeed.backfill(payloads);
    }
  }
}
