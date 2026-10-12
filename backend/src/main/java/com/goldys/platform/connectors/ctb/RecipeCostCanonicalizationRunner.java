package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.RecipeCostIngest;
import com.goldys.platform.canonical.RecipeCostInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off replay: canonicalizes the {@code ctb-recipe-cost} raw records already in the ledger (from
 * the detail backfill) into {@code canonical_recipe_cost}. Runs only when {@code
 * app.recipe-cost-canonicalize=true}, before the projection seeder, and is idempotent — the
 * canonical service dedupes on {@code source_record_ref}. Projection is driven by the {@code
 * RecipeCostRecorded} events each record publishes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.recipe-cost-canonicalize", havingValue = "true")
public class RecipeCostCanonicalizationRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(RecipeCostCanonicalizationRunner.class);

  private final RawLedgerQuery rawLedger;
  private final CtbRecipeCostParser parser;
  private final RecipeCostIngest ingest;

  public RecipeCostCanonicalizationRunner(
      RawLedgerQuery rawLedger, CtbRecipeCostParser parser, RecipeCostIngest ingest) {
    this.rawLedger = rawLedger;
    this.parser = parser;
    this.ingest = ingest;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<RecipeCostInput> all = new ArrayList<>();
    for (RawLedgerQuery.RawPayload payload :
        rawLedger.rawPayloadsForFetcher("CTB", "ctb-recipe-cost")) {
      all.addAll(parser.parse(payload.bytes(), payload.id()));
    }
    // One batch record → one projection rebuild, not one per recipe.
    ingest.recordAll(all);
    log.info("CTB recipe-cost canonicalization replayed {} recipes", all.size());
  }
}
