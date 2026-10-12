package com.goldys.platform.reporting;

/** Input for {@link ToolId#GET_RECIPE_COSTING}: the maximum number of recipes, or 0 for all. */
public record GetRecipeCostingInput(int limit) implements ToolInput {
  public GetRecipeCostingInput {
    if (limit < 0) {
      throw new IllegalArgumentException("limit must be non-negative");
    }
  }
}
