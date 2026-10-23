package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.List;

/**
 * A complete observed recipe: direct stock lines and explicit sub-recipe usages, never flattened.
 */
public record RecipeDefinition(
    RecipeHeader header, List<RecipeStock> stocks, List<RecipeUsage> usages, Instant observedAt) {
  public RecipeDefinition {
    stocks = List.copyOf(stocks);
    usages = List.copyOf(usages);
  }
}
