package com.goldys.platform.semantic;

import java.util.List;

/**
 * Business reads over resolved recipes. Implementations read the resolved projection; consumers
 * (the Recipes screen, reporting tools) never derive these figures from canonical source rows.
 */
public interface RecipeMetricsQuery {

  /** All resolved recipes, ascending by name, as raw facts. */
  List<RecipeSummary> recipes();

  /** All resolved recipes with the derived margin, gross-profit percent, and cost gap. */
  List<RecipeCosting> costing();
}
