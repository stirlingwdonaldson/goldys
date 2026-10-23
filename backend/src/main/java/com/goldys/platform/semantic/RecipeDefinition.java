package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.List;

/**
 * A complete observed recipe: direct stock lines and explicit sub-recipe usages, never flattened.
 */
public record RecipeDefinition(
    RecipeHeader header,
    List<RecipeStock> stocks,
    List<RecipeUsage> usages,
    Instant observedAt,
    List<java.util.UUID> rawRecordIds) {
  public RecipeDefinition(
      RecipeHeader header, List<RecipeStock> stocks, List<RecipeUsage> usages, Instant observedAt) {
    this(header, stocks, usages, observedAt, List.of());
  }

  public RecipeDefinition {
    rawRecordIds = List.copyOf(rawRecordIds);
    stocks = List.copyOf(stocks);
    usages = List.copyOf(usages);
  }
}
