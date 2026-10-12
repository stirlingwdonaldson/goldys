package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.RecipeIngredientIngest;
import com.goldys.platform.canonical.RecipeIngredientInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecipeIngredientCanonicalizationRunnerTest {

  @Test
  void replaysIngredientRawRecordsIntoCanonical() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    String json1 =
        "{\"data\":[{\"recipeDetailId\":1,\"recipeId\":627,\"stockId\":1,\"stockDesc\":\"Oil\"}]}";
    String json2 =
        "{\"data\":[{\"recipeDetailId\":2,\"recipeId\":628,\"stockId\":2,\"stockDesc\":\"Salt\"}]}";
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-recipe-ingredients"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), json1.getBytes(StandardCharsets.UTF_8), "recipe:627"),
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), json2.getBytes(StandardCharsets.UTF_8), "recipe:628")));

    RecipeIngredientIngest ingest = mock(RecipeIngredientIngest.class);
    RecipeIngredientCanonicalizationRunner runner =
        new RecipeIngredientCanonicalizationRunner(
            rawLedger, new CtbRecipeIngredientsParser(), ingest);

    runner.run(null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecipeIngredientInput>> captor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(ingest).recordAll(captor.capture());
    assertThat(captor.getValue())
        .extracting(RecipeIngredientInput::parentRecipeId)
        .containsExactly(627, 628);
  }

  @Test
  void skipsRecordsWithUnparseableSourceRecordRef() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    String json =
        "{\"data\":[{\"recipeDetailId\":1,\"recipeId\":627,\"stockId\":1,\"stockDesc\":\"Oil\"}]}";
    when(rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-recipe-ingredients"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), json.getBytes(StandardCharsets.UTF_8), null),
                new RawLedgerQuery.RawPayloadWithRef(
                    UUID.randomUUID(), json.getBytes(StandardCharsets.UTF_8), "recipe:628")));

    RecipeIngredientIngest ingest = mock(RecipeIngredientIngest.class);
    RecipeIngredientCanonicalizationRunner runner =
        new RecipeIngredientCanonicalizationRunner(
            rawLedger, new CtbRecipeIngredientsParser(), ingest);

    runner.run(null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecipeIngredientInput>> captor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(ingest).recordAll(captor.capture());
    assertThat(captor.getValue())
        .extracting(RecipeIngredientInput::parentRecipeId)
        .containsExactly(628);
  }
}
