package com.goldys.platform.reporting;

/**
 * Input for {@link ToolId#GET_RECIPE_INGREDIENTS}: the recipe whose ingredient lines to list, and
 * an optional cap on the number of lines (0 = all).
 */
public record GetRecipeIngredientsInput(int recipeId, int limit) implements ToolInput {
  public GetRecipeIngredientsInput {
    if (recipeId <= 0) {
      throw new IllegalArgumentException("recipeId must be positive");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("limit must be non-negative");
    }
  }

  /** Convenience constructor: all lines (no limit). */
  public GetRecipeIngredientsInput(int recipeId) {
    this(recipeId, 0);
  }
}
