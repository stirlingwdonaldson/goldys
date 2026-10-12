package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.RecipeCostIngest;
import com.goldys.platform.canonical.RecipeCostInput;
import com.goldys.platform.ingestion.RawLedgerQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecipeCostCanonicalizationRunnerTest {

  @Test
  void replaysRecipeCostRawRecordsIntoCanonical() {
    RawLedgerQuery rawLedger = mock(RawLedgerQuery.class);
    String json1 =
        "{\"data\":[{\"recipeId\":627,\"recipeName\":\"Parma\",\"costPerPortion\":6.00}]}";
    String json2 =
        "{\"data\":[{\"recipeId\":628,\"recipeName\":\"Risotto\",\"costPerPortion\":8.00}]}";
    when(rawLedger.rawPayloadsForFetcher("CTB", "ctb-recipe-cost"))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawPayload(
                    UUID.randomUUID(), json1.getBytes(StandardCharsets.UTF_8)),
                new RawLedgerQuery.RawPayload(
                    UUID.randomUUID(), json2.getBytes(StandardCharsets.UTF_8))));

    RecipeCostIngest ingest = mock(RecipeCostIngest.class);
    RecipeCostCanonicalizationRunner runner =
        new RecipeCostCanonicalizationRunner(rawLedger, new CtbRecipeCostParser(), ingest);

    runner.run(null);

    ArgumentCaptor<RecipeCostInput> captor = ArgumentCaptor.forClass(RecipeCostInput.class);
    verify(ingest, times(2)).record(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(RecipeCostInput::recipeId)
        .containsExactly(627, 628);
  }
}
