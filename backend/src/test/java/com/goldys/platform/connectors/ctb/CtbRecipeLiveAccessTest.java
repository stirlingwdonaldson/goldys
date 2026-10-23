package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicit opt-in, read-only access proof; credentials are never fixture data or test output. */
@EnabledIfEnvironmentVariable(named = "CTB_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CTB_PASSWORD", matches = ".+")
class CtbRecipeLiveAccessTest {
  @Test
  void authenticatedClientCanReadCompleteRecipesAndTraverseTheFolderCatalog() {
    CtbClient client = new CtbClient("https://web.cookingthebooks.com.au");
    client.login(System.getenv("CTB_EMAIL"), System.getenv("CTB_PASSWORD"));
    var h = client.getRecipeDefinition(696);
    var s = client.getRecipeDirectStocks(696);
    var u = client.getRecipeUsages(696);
    assertThat(h.status()).isEqualTo(200);
    assertThat(s.status()).isEqualTo(200);
    assertThat(u.status()).isEqualTo(200);
    var definition =
        new CtbRecipeDefinitionParser().parse(696, bytes(h), bytes(s), bytes(u), Instant.now());
    assertThat(definition.header().recipeId()).isEqualTo(696);
    CtbRecipeCatalogParser tree = new CtbRecipeCatalogParser();
    int requests = 0;
    while (!tree.pending().isEmpty()) {
      assertThat(++requests).isLessThan(1000);
      int folder = tree.pending().getFirst();
      var response = client.getRecipeFolder(folder);
      assertThat(response.status()).isEqualTo(200);
      tree.accept(folder, bytes(response));
    }
    var catalog = tree.finish(Instant.now());
    assertThat(catalog.folders()).isNotEmpty();
    System.out.printf(
        "Live CTB client proof: complete recipe parsed; %d folders traversed; %d explicit outlet allocations%n",
        requests, catalog.allocations().size());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "CTB_RECIPE_FULL_AUDIT", matches = "true")
  void everyListedRecipeCanBeReadAndItsCompositionParsed() throws Exception {
    var client = new CtbClient("https://web.cookingthebooks.com.au");
    client.login(System.getenv("CTB_EMAIL"), System.getenv("CTB_PASSWORD"));
    var listed = CtbRecipeDefinitionParser.array(client.getRecipeList().bytes());
    java.util.Map<Integer, String> names = new java.util.LinkedHashMap<>();
    for (var row : listed)
      names.put(
          CtbRecipeDefinitionParser.requiredInt(row, "recipeId"), row.path("recipeName").asText());
    var tree = new CtbRecipeCatalogParser();
    java.util.Set<Integer> treeIds = new java.util.HashSet<>();
    while (!tree.pending().isEmpty()) {
      Thread.sleep(250);
      int folder = tree.pending().getFirst();
      var response = client.getRecipeFolder(folder);
      assertThat(response.status()).isEqualTo(200);
      tree.accept(folder, response.bytes());
      collectRecipeIds(CtbRecipeDefinitionParser.json(response.bytes()), treeIds);
    }
    var parser = new CtbRecipeDefinitionParser();
    java.util.List<String> failures = new java.util.ArrayList<>();
    var lines = new java.util.ArrayList<String>();
    lines.add("recipe_id,recipe_name,in_folder_tree,composition_status");
    int complete = 0;
    for (var entry : names.entrySet()) {
      int id = entry.getKey();
      String status = "COMPLETE";
      try {
        Thread.sleep(250);
        var h = client.getRecipeDefinition(id);
        Thread.sleep(250);
        var stocks = client.getRecipeDirectStocks(id);
        Thread.sleep(250);
        var usages = client.getRecipeUsages(id);
        assertThat(h.status()).isEqualTo(200);
        assertThat(stocks.status()).isEqualTo(200);
        assertThat(usages.status()).isEqualTo(200);
        parser.parse(id, h.bytes(), stocks.bytes(), usages.bytes(), Instant.now());
        complete++;
      } catch (RuntimeException | AssertionError e) {
        status = "FAILED";
        failures.add(Integer.toString(id));
      }
      lines.add(
          id
              + ",\""
              + entry.getValue().replace("\"", "\"\"")
              + "\","
              + treeIds.contains(id)
              + ","
              + status);
    }
    String report = System.getenv("CTB_RECIPE_AUDIT_REPORT");
    if (report != null) java.nio.file.Files.write(java.nio.file.Path.of(report), lines);
    var sectionParser = new CtbRecipeSectionParser();
    int optional = 0;
    for (String section :
        java.util.List.of(
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
            "HEADING")) {
      Thread.sleep(250);
      var response = client.getRecipeSection(377, section);
      assertThat(response.status()).isEqualTo(200);
      sectionParser.parse(
          377,
          section,
          response.bytes(),
          new com.goldys.platform.semantic.RecipeEvidence(
              java.util.UUID.randomUUID(),
              "CTB",
              "live-audit",
              CtbRecipeRefresh.sectionEndpoint(section),
              null,
              Instant.now(),
              null,
              null));
      optional++;
    }
    long listOnly = names.keySet().stream().filter(id -> !treeIds.contains(id)).count();
    long treeOnly = treeIds.stream().filter(id -> !names.containsKey(id)).count();
    System.out.printf(
        "Live recipe audit: %d listed, %d complete, %d failed; %d tree recipe IDs, %d list-only, %d tree-only; %d optional endpoints parsed%n",
        names.size(), complete, failures.size(), treeIds.size(), listOnly, treeOnly, optional);
    assertThat(failures)
        .as("Recipe IDs without a complete parse; inspect the local audit CSV")
        .isEmpty();
  }

  private void collectRecipeIds(
      com.fasterxml.jackson.databind.JsonNode node, java.util.Set<Integer> ids) {
    if (node.isArray()) {
      node.forEach(n -> collectRecipeIds(n, ids));
      return;
    }
    String id = node.path("id").asText();
    if (id.startsWith("recipe;") && !id.endsWith("loadingindicator"))
      ids.add(Integer.parseInt(id.substring(7)));
    node.path("children").forEach(n -> collectRecipeIds(n, ids));
  }

  private byte[] bytes(CtbClient.RecipeResponse response) {
    return response.json().getBytes(StandardCharsets.UTF_8);
  }
}
