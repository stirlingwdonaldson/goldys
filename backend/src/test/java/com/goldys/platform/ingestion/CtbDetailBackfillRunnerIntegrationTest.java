package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.connectors.ctb.CtbClient;
import com.goldys.platform.connectors.ctb.CtbDetailBackfillConnector;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * End-to-end: the backfill connector, run through {@link ConnectorRunner}, lands one run + four
 * identities.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CtbDetailBackfillRunnerIntegrationTest {
  @Autowired ConnectorRunner runner;
  @Autowired IngestionRunRepository runs;
  @Autowired RawRecordRepository rawRecords;

  @Test
  void backfillRunPersistsOneRunAndTheFourDetailIdentities() {
    CtbClient client = stubClient();
    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");

    UUID runId = runner.run(connector, null);

    assertThat(awaitTerminal(runId)).isEqualTo(IngestionStatus.SUCCESS);
    IngestionRun run = runs.findById(runId).orElseThrow();
    assertThat(run.sourceSystem()).isEqualTo("CTB");
    assertThat(run.connectorName()).isEqualTo("ctb-detail-backfill");
    assertThat(rawRecords.findByIngestionRunId(runId))
        .extracting(RawRecord::fetcherIdentity)
        .containsExactlyInAnyOrder(
            "ctb-stocktake-detail",
            "ctb-wastage-detail",
            "ctb-recipe-ingredients",
            "ctb-recipe-ingredients",
            "ctb-recipe-cost");
    assertThat(rawRecords.findByIngestionRunId(runId))
        .allSatisfy(
            r -> {
              assertThat(r.sourceSystem()).isEqualTo("CTB");
              assertThat(r.fetchMethod()).isEqualTo(FetchMethod.API);
              assertThat(r.contentType()).isEqualTo("application/json");
            });
  }

  private static CtbClient stubClient() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"wastageRecordId\":60}],\"totalCount\":1}", 1));
    when(client.getAllRecipes())
        .thenReturn(page("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getWastageRecordDetail(anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":2}", 2));
    return client;
  }

  private static CtbClient.CtbPage page(String json, int total) {
    return new CtbClient.CtbPage(json, total);
  }

  /** Polls the run until it leaves RUNNING, then returns its terminal status. */
  private IngestionStatus awaitTerminal(UUID runId) {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      IngestionRun run = runs.findById(runId).orElseThrow();
      if (run.status() != IngestionStatus.RUNNING) {
        return run.status();
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while awaiting run completion", e);
      }
    }
    throw new AssertionError("run " + runId + " did not reach a terminal state");
  }
}
