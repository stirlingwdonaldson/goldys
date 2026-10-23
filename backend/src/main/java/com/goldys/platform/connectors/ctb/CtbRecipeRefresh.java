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
  static final List<String> SECTIONS =
      List.of(
          "EQUIPMENT",
          "NUTRITION",
          "DIETARY",
          "ALLERGENS",
          "INGREDIENT_METHODS",
          "MAJOR_FLAVOUR",
          "MAJOR_INGREDIENT",
          "COOKING_METHOD",
          "SECTION",
          "DISH_TYPE",
          "HEADING");
  static final String HEADER = "ctb-recipe-definition";
  static final String STOCKS = "ctb-recipe-direct-stocks";
  static final String USAGES = "ctb-recipe-usages";
  static final String FOLDERS = "ctb-recipe-folder-tree";
  private final RecipeDefinitionIngest ingest;
  private com.goldys.platform.canonical.RecipeSectionIngest sections;
  private final CtbRecipeSectionParser sectionParser = new CtbRecipeSectionParser();

  @org.springframework.beans.factory.annotation.Autowired
  public void setSections(com.goldys.platform.canonical.RecipeSectionIngest sections) {
    this.sections = sections;
  }

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
    recordSection(0, "LIST", list, "/RecipeBook/GetAllRecipes");
    if (sections != null)
      optional(client, 0, "EQUIPMENT_OPTIONS", client::getRecipeEquipmentOptions, sink, failures);
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
      if (sections != null)
        for (String section : SECTIONS)
          optional(client, id, section, () -> client.getRecipeSection(id, section), sink, failures);
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
    com.goldys.platform.semantic.RecipeDefinition definition;
    try {
      definition = parser.parse(id, h.bytes(), s.bytes(), u.bytes(), h.observedAt());
    } catch (ConnectorFetchException e) {
      failure(
          id,
          "HEADER",
          h,
          "/RecipeBook/GetRecipeByRecipeId",
          "Complete composition rejected: " + e.getMessage());
      throw e;
    }
    ingest.record(definition, h.id(), s.id(), u.id());
    recordSection(id, "HEADER", h, "/RecipeBook/GetRecipeByRecipeId");
    recordSection(id, "STOCKS", s, "/RecipeBook/GetRecipeDetailByRecipeId");
    recordSection(id, "USAGES", u, "/RecipeBook/GetIngredientFromExistingRecipesByRecipeId");
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
      recordSection(0, "FOLDER_" + folder, page, "/RecipeFolder/GetTreeItemsUnderRecipeFolder");
    }
    ingest.recordCatalog(tree.finish(observedAt), evidence);
  }

  private void recordSection(int id, String section, Captured page, String endpoint) {
    if (sections != null)
      sections.record(
          sectionParser.parse(id, section, page.bytes(), raw.recipeEvidence(page.id(), endpoint)));
  }

  static String sectionEndpoint(String section) {
    return switch (section) {
      case "EQUIPMENT_OPTIONS" -> "/Equipment/SearchEquipments";
      case "EQUIPMENT" -> "/RecipeEquipment/SearchRecipeEquipments";
      case "NUTRITION" -> "/RecipeBook/GetRecipeNutritionInformation";
      case "DIETARY" -> "/DietaryRequirement/GetRecipeDietaryRequirement";
      case "ALLERGENS" -> "/DietaryRequirement/GetRecipeDietaryAllergens";
      case "INGREDIENT_METHODS" -> "/RecipeBook/GetIngredientMethodsByRecipeId";
      case "MAJOR_FLAVOUR",
          "MAJOR_INGREDIENT",
          "COOKING_METHOD",
          "SECTION",
          "DISH_TYPE",
          "HEADING" ->
          "/RecipeBook/GetRecipeFactsByRecipeId";
      default -> throw new IllegalArgumentException("Unknown recipe section");
    };
  }

  private void optional(
      CtbClient client,
      int id,
      String section,
      Supplier<CtbClient.RecipeResponse> request,
      IngestionSink sink,
      List<String> failures) {
    Captured page = null;
    try {
      page =
          receive(
              request,
              "ctb-recipe-section-" + section.toLowerCase(java.util.Locale.ROOT),
              "recipe:" + id + ":section:" + section,
              sink);
      checkStatus(page);
      recordSection(id, section, page, sectionEndpoint(section));
    } catch (ConnectorFetchException e) {
      if (page != null) failure(id, section, page, sectionEndpoint(section), e.getMessage());
      failures.add("recipe:" + id + ":" + section + ":" + e.failureType());
    }
  }

  private void failure(
      int recipeId, String section, Captured page, String endpoint, String message) {
    if (sections != null)
      sections.record(
          new com.goldys.platform.semantic.RecipeSection(
              recipeId,
              section,
              false,
              message,
              List.of(),
              raw.recipeEvidence(page.id(), endpoint)));
  }

  private void checkStatus(Captured page) {
    if (page.status() < 200 || page.status() >= 300)
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Recipe request returned HTTP " + page.status());
  }

  private Captured capture(
      Supplier<CtbClient.RecipeResponse> request, String fetcher, String ref, IngestionSink sink) {
    Captured page = receive(request, fetcher, ref, sink);
    try {
      checkStatus(page);
    } catch (ConnectorFetchException e) {
      recordCoreFailure(fetcher, ref, page, e.getMessage());
      throw e;
    }
    return page;
  }

  private void recordCoreFailure(String fetcher, String ref, Captured page, String message) {
    if (!ref.startsWith("recipe:")) return;
    String section =
        switch (fetcher) {
          case HEADER -> "HEADER";
          case STOCKS -> "STOCKS";
          case USAGES -> "USAGES";
          default -> throw new IllegalArgumentException("Unknown composition response");
        };
    String endpoint =
        switch (section) {
          case "HEADER" -> "/RecipeBook/GetRecipeByRecipeId";
          case "STOCKS" -> "/RecipeBook/GetRecipeDetailByRecipeId";
          default -> "/RecipeBook/GetIngredientFromExistingRecipesByRecipeId";
        };
    failure(Integer.parseInt(ref.split(":")[1]), section, page, endpoint, message);
  }

  private Captured receive(
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
    return new Captured(id, bytes, raw.rawRecordRef(id).fetchedAt(), response.status());
  }

  private record Captured(UUID id, byte[] bytes, Instant observedAt, int status) {}
}
