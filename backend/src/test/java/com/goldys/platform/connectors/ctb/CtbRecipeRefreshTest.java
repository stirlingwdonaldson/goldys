package com.goldys.platform.connectors.ctb;

import static com.goldys.platform.support.RecipeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.canonical.RecipeDefinitionIngest;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.*;
import com.goldys.platform.semantic.RecipeDefinition;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CtbRecipeRefreshTest {
  @Test
  void partialRecipeCaptureIsStoredButDoesNotReplacePreviousCompleteState() {
    var client = mock(CtbClient.class);
    var ingest = mock(RecipeDefinitionIngest.class);
    var raw = mock(RawLedgerQuery.class);
    when(client.getRecipeFolder(0))
        .thenReturn(
            response(
                "[{\"id\":\"folder;0\",\"text\":\"Recipes\",\"children\":[],\"outlets\":[]}]"));
    when(client.getRecipeList())
        .thenReturn(response("{\"data\":[{\"recipeId\":651},{\"recipeId\":696}]}"));
    when(client.getRecipeDefinition(651)).thenReturn(response(text("third_651_header.json")));
    when(client.getRecipeDirectStocks(651))
        .thenReturn(new CtbClient.RecipeResponse("{\"message\":{\"IsSuccess\":false}}", 500));
    when(client.getRecipeDefinition(696)).thenReturn(response(text("third_696_header.json")));
    when(client.getRecipeDirectStocks(696)).thenReturn(response(text("third_696_stocks.json")));
    when(client.getRecipeUsages(696)).thenReturn(response(text("third_696_usages.json")));
    Instant captured = Instant.parse("2026-10-23T01:00:00Z");
    when(raw.rawRecordRef(any()))
        .thenAnswer(call -> new RawLedgerQuery.RawRecordRef(call.getArgument(0), null, captured));
    List<FetchedPayload> stored = new ArrayList<>();
    var refresh = new CtbRecipeRefresh(ingest, new CtbRecipeDefinitionParser(), raw, 0, 10, 10);
    assertThatThrownBy(
            () ->
                refresh.fetch(
                    client,
                    p -> {
                      stored.add(p);
                      return UUID.randomUUID();
                    }))
        .isInstanceOf(ConnectorFetchException.class)
        .hasMessageContaining("recipe:651");
    assertThat(stored).anySatisfy(p -> assertThat(p.sourceRecordRef()).endsWith(":http:500"));
    var definition = ArgumentCaptor.forClass(RecipeDefinition.class);
    verify(ingest, times(1)).record(definition.capture(), any(), any(), any());
    assertThat(definition.getValue().header().recipeId()).isEqualTo(696);
    assertThat(definition.getValue().observedAt()).isEqualTo(captured);
    verify(client, never()).getRecipeUsages(651);
  }

  @Test
  void replayNeverCombinesDifferentCapturesOrInflatesObservationTime() {
    var raw = mock(RawLedgerQuery.class);
    var ingest = mock(RecipeDefinitionIngest.class);
    UUID h = UUID.randomUUID(), s = UUID.randomUUID(), u = UUID.randomUUID();
    Instant captured = Instant.parse("2026-10-20T00:00:00Z");
    String capture = "recipe:651:capture:" + UUID.randomUUID() + ":http:200";
    when(raw.rawRecordRefsForFetcher("CTB", CtbRecipeRefresh.HEADER))
        .thenReturn(List.of(new RawLedgerQuery.RawRecordRef(h, capture, captured)));
    when(raw.rawRecordRefsForFetcher("CTB", CtbRecipeRefresh.STOCKS))
        .thenReturn(List.of(new RawLedgerQuery.RawRecordRef(s, capture, captured)));
    when(raw.rawRecordRefsForFetcher("CTB", CtbRecipeRefresh.USAGES))
        .thenReturn(
            List.of(
                new RawLedgerQuery.RawRecordRef(
                    u, "recipe:651:capture:" + UUID.randomUUID() + ":http:200", captured)));
    var replay = new CtbRecipeReplay(raw, ingest, new CtbRecipeDefinitionParser());
    assertThat(replay.replayRecipes()).isZero();
    verifyNoInteractions(ingest);
    when(raw.rawRecordRefsForFetcher("CTB", CtbRecipeRefresh.USAGES))
        .thenReturn(List.of(new RawLedgerQuery.RawRecordRef(u, capture, captured)));
    when(raw.payloadBytes(h)).thenReturn(bytes("third_651_header.json"));
    when(raw.payloadBytes(s)).thenReturn(bytes("third_651_stocks.json"));
    when(raw.payloadBytes(u)).thenReturn(bytes("third_651_usages.json"));
    assertThat(replay.replayRecipes()).isEqualTo(1);
    var definition = ArgumentCaptor.forClass(RecipeDefinition.class);
    verify(ingest).record(definition.capture(), eq(h), eq(s), eq(u));
    assertThat(definition.getValue().observedAt()).isEqualTo(captured);
  }

  private static CtbClient.RecipeResponse response(String json) {
    return new CtbClient.RecipeResponse(json, 200);
  }

  private static String text(String file) {
    return new String(bytes(file), StandardCharsets.UTF_8);
  }
}
