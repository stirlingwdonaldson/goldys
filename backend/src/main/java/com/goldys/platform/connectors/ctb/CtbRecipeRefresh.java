package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.RecipeDefinitionIngest;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.port.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Paced recipe refresh inside the existing CTB connector's source lock and session. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "ctb.recipes.enabled",
    havingValue = "true")
public class CtbRecipeRefresh {
  static final String HEADER = "ctb-recipe-definition";
  static final String STOCKS = "ctb-recipe-direct-stocks";
  static final String USAGES = "ctb-recipe-usages";
  static final String FOLDERS = "ctb-recipe-folder-tree";
  private final RecipeDefinitionIngest ingest;
  private final CtbRecipeDefinitionParser parser;
  private final RawLedgerQuery raw;
  private final long delayMillis;
  private final int maxRecipes;
  private final int maxFolders;

  public CtbRecipeRefresh(
      RecipeDefinitionIngest ingest,
      CtbRecipeDefinitionParser parser,
      RawLedgerQuery raw,
      @org.springframework.beans.factory.annotation.Value("${ctb.recipes.delay-millis:250}")
          long delayMillis,
      @org.springframework.beans.factory.annotation.Value("${ctb.recipes.max-recipes:5000}")
          int maxRecipes,
      @org.springframework.beans.factory.annotation.Value("${ctb.recipes.max-folders:1000}")
          int maxFolders) {
    if (delayMillis < 0 || maxRecipes < 1 || maxFolders < 1)
      throw new IllegalArgumentException("Invalid recipe refresh limits");
    this.ingest = ingest;
    this.parser = parser;
    this.raw = raw;
    this.delayMillis = delayMillis;
    this.maxRecipes = maxRecipes;
    this.maxFolders = maxFolders;
  }

  public void fetch(CtbClient client, IngestionSink sink) {
    List<String> failures = new ArrayList<>();
    try {
      catalog(client, sink);
    } catch (ConnectorFetchException e) {
      failures.add("catalog:" + e.failureType());
    }
    Captured list = capture(client::getRecipeList, "ctb-recipes", "recipe-refresh-list", sink);
    Set<Integer> ids = new LinkedHashSet<>();
    for (var row : CtbRecipeDefinitionParser.array(list.bytes()))
      ids.add(CtbRecipeDefinitionParser.requiredInt(row, "recipeId"));
    if (ids.size() > maxRecipes)
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_TRUNCATED", "Recipe refresh exceeded configured recipe limit");
    for (int id : ids) {
      try {
        recipe(client, id, sink);
      } catch (ConnectorFetchException e) {
        failures.add("recipe:" + id + ":" + e.failureType());
      }
    }
    if (!failures.isEmpty())
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Recipe refresh incomplete: " + String.join(", ", failures));
  }

  private void recipe(CtbClient client, int id, IngestionSink sink) {
    String ref = "recipe:" + id + ":capture:" + UUID.randomUUID();
    Captured h = capture(() -> client.getRecipeDefinition(id), HEADER, ref, sink);
    Captured s = capture(() -> client.getRecipeDirectStocks(id), STOCKS, ref, sink);
    Captured u = capture(() -> client.getRecipeUsages(id), USAGES, ref, sink);
    ingest.record(
        parser.parse(id, h.bytes(), s.bytes(), u.bytes(), h.observedAt()), h.id(), s.id(), u.id());
  }

  private void catalog(CtbClient client, IngestionSink sink) {
    CtbRecipeCatalogParser tree = new CtbRecipeCatalogParser();
    List<UUID> evidence = new ArrayList<>();
    String prefix = "catalog:" + UUID.randomUUID() + ":folder:";
    Instant observedAt = null;
    while (!tree.pending().isEmpty()) {
      int folder = tree.pending().getFirst();
      if (evidence.size() >= maxFolders)
        throw new ConnectorFetchException(
            "CONNECTOR_FETCH_TRUNCATED", "Recipe folder refresh exceeded configured limit");
      Captured page = capture(() -> client.getRecipeFolder(folder), FOLDERS, prefix + folder, sink);
      if (observedAt == null) observedAt = page.observedAt();
      evidence.add(page.id());
      tree.accept(folder, page.bytes());
    }
    ingest.recordCatalog(tree.finish(observedAt), evidence);
  }

  private Captured capture(
      Supplier<CtbClient.RecipeResponse> request, String fetcher, String ref, IngestionSink sink) {
    if (delayMillis > 0) {
      try {
        Thread.sleep(delayMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ConnectorFetchException(
            "CONNECTOR_FETCH_FAILED", "Recipe refresh interrupted", e);
      }
    }
    CtbClient.RecipeResponse response = request.get();
    byte[] bytes = response.bytes();
    UUID id =
        sink.accept(
            new FetchedPayload(
                FetchMethod.API,
                response.contentType(),
                bytes,
                response.contentType().toLowerCase(java.util.Locale.ROOT).contains("json")
                    ? StandardCharsets.UTF_8.name()
                    : null,
                fetcher,
                ref + ":http:" + response.status()));
    if (response.status() < 200 || response.status() >= 300)
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Recipe request returned HTTP " + response.status());
    return new Captured(id, bytes, raw.rawRecordRef(id).fetchedAt());
  }

  private record Captured(UUID id, byte[] bytes, Instant observedAt) {}
}
