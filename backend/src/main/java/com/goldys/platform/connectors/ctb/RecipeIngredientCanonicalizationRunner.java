package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.RecipeIngredientIngest;
import com.goldys.platform.canonical.RecipeIngredientInput;
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
 * One-off replay: canonicalizes the {@code ctb-recipe-ingredients} raw records already in the
 * ledger (from the detail backfill) into {@code canonical_recipe_ingredient}. Runs only when {@code
 * app.recipe-ingredients-canonicalize=true}, before the projection seeder, and is idempotent — the
 * canonical service dedupes on {@code source_record_ref}. Projection is driven by the {@code
 * RecipeIngredientRecorded} event the batch record publishes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.recipe-ingredients-canonicalize", havingValue = "true")
public class RecipeIngredientCanonicalizationRunner implements ApplicationRunner {
  private static final Logger log =
      LoggerFactory.getLogger(RecipeIngredientCanonicalizationRunner.class);

  private final RawLedgerQuery rawLedger;
  private final CtbRecipeIngredientsParser parser;
  private final RecipeIngredientIngest ingest;

  public RecipeIngredientCanonicalizationRunner(
      RawLedgerQuery rawLedger, CtbRecipeIngredientsParser parser, RecipeIngredientIngest ingest) {
    this.rawLedger = rawLedger;
    this.parser = parser;
    this.ingest = ingest;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<RecipeIngredientInput> all = new ArrayList<>();
    for (RawLedgerQuery.RawPayloadWithRef payload :
        rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-recipe-ingredients")) {
      Integer parentRecipeId = parentRecipeId(payload.sourceRecordRef());
      if (parentRecipeId == null) {
        log.warn(
            "Skipping ctb-recipe-ingredients record {} with unparseable source_record_ref {}",
            payload.id(),
            payload.sourceRecordRef());
        continue;
      }
      all.addAll(parser.parse(payload.bytes(), payload.id(), parentRecipeId));
    }
    // One batch record → one projection rebuild, not one per line.
    ingest.recordAll(all);
    log.info("CTB recipe-ingredient canonicalization replayed {} lines", all.size());
  }

  private static Integer parentRecipeId(String sourceRecordRef) {
    if (sourceRecordRef == null || !sourceRecordRef.startsWith("recipe:")) {
      return null;
    }
    try {
      return Integer.parseInt(sourceRecordRef.substring("recipe:".length()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
