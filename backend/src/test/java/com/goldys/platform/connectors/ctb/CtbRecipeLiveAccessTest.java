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

  private byte[] bytes(CtbClient.RecipeResponse response) {
    return response.json().getBytes(StandardCharsets.UTF_8);
  }
}
