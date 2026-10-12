package com.goldys.platform.semantic;

import java.util.List;

/**
 * Business reads over resolved recipe ingredients. Implementations read the resolved projection;
 * consumers (the Recipes screen, reporting tools) never derive these from canonical source rows.
 */
public interface RecipeIngredientMetricsQuery {

  /** One recipe's ingredient lines (main lines before sub-recipe lines), ascending by recipe. */
  List<RecipeIngredient> ingredientsFor(int recipeId);
}
