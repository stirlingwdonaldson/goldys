package com.goldys.platform.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.connectors.ctb.CtbRecipeDefinitionParser;
import com.goldys.platform.semantic.RecipeDefinition;
import java.io.IOException;
import java.time.Instant;

public final class RecipeFixtures {
  private RecipeFixtures() {}

  public static byte[] bytes(String file) {
    try (var stream = RecipeFixtures.class.getResourceAsStream("/fixtures/ctb/recipes/" + file)) {
      if (stream == null) throw new IllegalArgumentException(file);
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static JsonNode manifest(String file) {
    try {
      return new ObjectMapper().readTree(bytes(file));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static RecipeDefinition definition(String key) {
    return new CtbRecipeDefinitionParser()
        .parse(
            Integer.parseInt(key.split("_")[1]),
            bytes(key + "_header.json"),
            bytes(key + "_stocks.json"),
            bytes(key + "_usages.json"),
            Instant.parse("2026-10-23T00:00:00Z"));
  }
}
