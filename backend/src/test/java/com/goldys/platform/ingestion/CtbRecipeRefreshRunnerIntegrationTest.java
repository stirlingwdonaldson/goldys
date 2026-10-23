package com.goldys.platform.ingestion;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.RecipeDefinitionIngest;
import com.goldys.platform.connectors.ctb.*;
import com.goldys.platform.ingestion.port.*;
import com.goldys.platform.semantic.*;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CtbRecipeRefreshRunnerIntegrationTest {
  @Autowired ConnectorRunner runner;
  @Autowired IngestionRunRepository runs;
  @Autowired RawRecordRepository records;
  @Autowired RawLedgerQuery raw;
  @Autowired IngestionService ingestion;
  @Autowired RecipeDefinitionIngest ingest;
  @Autowired com.goldys.platform.canonical.RecipeSectionIngest sections;
  @Autowired RecipeCompositionQuery query;

  @Test
  void partialRefreshRetainsOldRecipeStoresFailureBytesAndRefreshesOtherRecipes() throws Exception {
    var old = definition("third_651");
    var previous =
        new RecipeDefinition(
            old.header(),
            old.stocks(),
            old.usages(),
            Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    ingest.record(
        previous,
        store("third_651_header.json"),
        store("third_651_stocks.json"),
        store("third_651_usages.json"));
    CtbClient client = mock(CtbClient.class);
    when(client.getRecipeFolder(0))
        .thenReturn(
            response(
                "[{\"id\":\"folder;0\",\"text\":\"Recipes\",\"children\":[],\"outlets\":[]}]"));
    when(client.getRecipeList())
        .thenReturn(response("{\"data\":[{\"recipeId\":651},{\"recipeId\":696}]}"));
    when(client.getRecipeDefinition(651)).thenReturn(response(text("third_651_header.json")));
    byte[] failure = {(byte) 0xff, 0, 65};
    when(client.getRecipeDirectStocks(651))
        .thenReturn(new CtbClient.RecipeResponse(failure, 503, "application/octet-stream"));
    when(client.getRecipeDefinition(696)).thenReturn(response(text("third_696_header.json")));
    when(client.getRecipeDirectStocks(696)).thenReturn(response(text("third_696_stocks.json")));
    when(client.getRecipeUsages(696)).thenReturn(response(text("third_696_usages.json")));
    when(client.getRecipeEquipmentOptions()).thenReturn(response("{\"data\":[],\"totalCount\":0}"));
    when(client.getRecipeSection(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            call ->
                response(
                    call.getArgument(1).equals("NUTRITION") ? "{\"data\":{}}" : "{\"data\":[]}"));
    var refresh = new CtbRecipeRefresh(ingest, new CtbRecipeDefinitionParser(), raw, 0, 10, 10);
    refresh.setSections(sections);
    SourceConnector connector =
        new SourceConnector() {
          public String sourceSystem() {
            return "CTB";
          }

          public String connectorName() {
            return "recipe-refresh-test";
          }

          public void fetch(String watermark, IngestionSink sink) {
            refresh.fetch(client, sink);
          }
        };
    UUID runId = runner.run(connector, null);
    IngestionStatus status = null;
    for (int i = 0; i < 200; i++) {
      status = runs.findById(runId).orElseThrow().status();
      if (status != IngestionStatus.RUNNING) break;
      Thread.sleep(50);
    }
    assertThat(status).isEqualTo(IngestionStatus.PARTIAL);
    assertThat(records.findByIngestionRunId(runId))
        .hasSize(30)
        .anySatisfy(
            row -> {
              assertThat(row.sourceRecordRef()).endsWith(":http:503");
              assertThat(row.payloadBytes()).containsExactly(failure);
              assertThat(row.contentType()).isEqualTo("application/octet-stream");
            });
    assertThat(query.composition(651).orElseThrow().definition().observedAt())
        .isEqualTo(previous.observedAt());
    assertThat(query.composition(696).orElseThrow().definition().header().portionCount())
        .isEqualByComparingTo("15");
  }

  private UUID store(String file) {
    return ingestion.ingestPush(
        "CTB",
        "recipe-test",
        FetchMethod.API,
        "application/json",
        bytes(file),
        "UTF-8",
        "recipe-test");
  }

  private static String text(String file) {
    return new String(bytes(file), StandardCharsets.UTF_8);
  }

  private static CtbClient.RecipeResponse response(String json) {
    return new CtbClient.RecipeResponse(json, 200);
  }
}
