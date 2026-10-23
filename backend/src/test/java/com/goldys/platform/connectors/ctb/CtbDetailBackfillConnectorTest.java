package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import com.goldys.platform.ingestion.port.FetchedPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtbDetailBackfillConnectorTest {

  private static CtbClient.CtbPage page(String json, int total) {
    return new CtbClient.CtbPage(json, total);
  }

  @Test
  void detailPayloadsCarryTheirParentIdAsSourceRecordRef() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(
            page("{\"data\":[{\"stocktakeId\":38},{\"stocktakeId\":39}],\"totalCount\":2}", 2));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"wastageRecordId\":60}],\"totalCount\":1}", 1));
    when(client.getAllRecipes())
        .thenReturn(page("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":2}", 2));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();
    connector.fetch(
        null,
        p -> {
          written.add(p);
          return UUID.randomUUID();
        });

    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-stocktake-detail"))
                .map(FetchedPayload::sourceRecordRef))
        .containsExactlyInAnyOrder("stocktake:38", "stocktake:39");
    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-wastage-detail"))
                .map(FetchedPayload::sourceRecordRef))
        .containsExactly("wastage:60");
    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-recipe-ingredients"))
                .map(FetchedPayload::sourceRecordRef))
        .containsExactlyInAnyOrder("recipe:627", "recipe:628");
    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-recipe-cost"))
                .map(FetchedPayload::sourceRecordRef))
        .containsExactly((String) null);
  }

  @Test
  void oneRawWritePerDetailIdWithCorrectIdentity() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(
            page("{\"data\":[{\"stocktakeId\":38},{\"stocktakeId\":39}],\"totalCount\":2}", 2));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"wastageRecordId\":60}],\"totalCount\":1}", 1));
    when(client.getAllRecipes())
        .thenReturn(page("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getWastageRecordDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":2}", 2));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();

    connector.fetch(
        null,
        p -> {
          written.add(p);
          return UUID.randomUUID();
        });

    assertThat(written.stream().map(FetchedPayload::fetcherIdentity))
        .containsExactlyInAnyOrder(
            "ctb-stocktake-detail",
            "ctb-stocktake-detail",
            "ctb-wastage-detail",
            "ctb-recipe-ingredients",
            "ctb-recipe-ingredients",
            "ctb-recipe-cost");
    assertThat(written)
        .allSatisfy(
            p -> {
              assertThat(p.fetchMethod()).isEqualTo(FetchMethod.API);
              assertThat(p.contentType()).isEqualTo("application/json");
              assertThat(p.characterEncoding()).isEqualTo("UTF-8");
            });
  }

  @Test
  void stocktakeDetailPagesUntilTotalCount() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.getAllRecipes()).thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.getStocktakeDetail(38, 0, 1000))
        .thenReturn(page("{\"data\":[{\"line\":1}],\"totalCount\":1500}", 1500));
    when(client.getStocktakeDetail(38, 1000, 1000))
        .thenReturn(page("{\"data\":[{\"line\":2}],\"totalCount\":1500}", 1500));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();
    connector.fetch(
        null,
        p -> {
          written.add(p);
          return UUID.randomUUID();
        });

    verify(client).getStocktakeDetail(38, 0, 1000);
    verify(client).getStocktakeDetail(38, 1000, 1000);
    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-stocktake-detail"))
                .count())
        .isEqualTo(2);
  }

  @Test
  void recipeCostUsesJsonArrayRecipeIds() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.getAllRecipes())
        .thenReturn(page("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getIngredientsIncludingSubRecipeIngredients(anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":2}", 2));

    new CtbDetailBackfillConnector(client, "e", "p").fetch(null, p -> UUID.randomUUID());

    verify(client).getSelectedRecipeFullRecords("[627,628]");
  }

  @Test
  void perIdFailureIsIsolatedAndAggregated() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"wastageRecordId\":60}],\"totalCount\":1}", 1));
    when(client.getAllRecipes())
        .thenReturn(page("{\"data\":[{\"recipeId\":627},{\"recipeId\":628}],\"totalCount\":2}", 2));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getWastageRecordDetail(eq(60), anyInt(), anyInt()))
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_FETCH_FAILED", "CTB WastageRecord detail"));
    when(client.getIngredientsIncludingSubRecipeIngredients(627))
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));
    when(client.getIngredientsIncludingSubRecipeIngredients(628))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));
    when(client.getSelectedRecipeFullRecords(anyString()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":2}", 2));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();

    assertThatThrownBy(
            () ->
                connector.fetch(
                    null,
                    p -> {
                      written.add(p);
                      return UUID.randomUUID();
                    }))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("ctb-recipe-ingredients#627")
        .hasMessageContaining("ctb-wastage-detail#60");

    // 628 and the stocktake still land despite 627 and 60 failing.
    assertThat(written.stream().map(FetchedPayload::fetcherIdentity))
        .contains("ctb-recipe-ingredients", "ctb-stocktake-detail", "ctb-recipe-cost")
        .doesNotContain("ctb-wastage-detail");
    assertThat(
            written.stream()
                .filter(p -> p.fetcherIdentity().equals("ctb-recipe-ingredients"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void emptyHeaderListsWriteNothingAndDoNotThrow() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.getAllRecipes()).thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();

    connector.fetch(
        null,
        p -> {
          written.add(p);
          return UUID.randomUUID();
        });

    assertThat(written).isEmpty();
    verify(client, never()).getSelectedRecipeFullRecords(anyString());
  }

  @Test
  void aFailingHeaderListSkipsItsFanoutButOthersRun() {
    CtbClient client = mock(CtbClient.class);
    when(client.searchStocktakes(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{\"stocktakeId\":38}],\"totalCount\":1}", 1));
    when(client.searchWastageRecords(anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[],\"totalCount\":0}", 0));
    when(client.getAllRecipes())
        .thenThrow(
            new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON CTB response"));
    when(client.getStocktakeDetail(anyInt(), anyInt(), anyInt()))
        .thenReturn(page("{\"data\":[{}],\"totalCount\":1}", 1));

    CtbDetailBackfillConnector connector = new CtbDetailBackfillConnector(client, "e", "p");
    List<FetchedPayload> written = new ArrayList<>();

    assertThatThrownBy(
            () ->
                connector.fetch(
                    null,
                    p -> {
                      written.add(p);
                      return UUID.randomUUID();
                    }))
        .isInstanceOf(ConnectorFetchException.class)
        .extracting(e -> ((ConnectorFetchException) e).failureType())
        .isEqualTo("CONNECTOR_SCHEMA_MISMATCH");
    assertThat(written.stream().map(FetchedPayload::fetcherIdentity))
        .contains("ctb-stocktake-detail");
    verify(client, never()).getSelectedRecipeFullRecords(anyString());
  }
}
