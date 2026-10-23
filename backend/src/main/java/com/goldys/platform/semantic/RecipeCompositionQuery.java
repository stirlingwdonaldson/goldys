package com.goldys.platform.semantic;

import java.util.Optional;

public interface RecipeCompositionQuery {
  Optional<RecipeComposition> composition(int recipeId);

  Optional<RecipeCatalog> catalog();
}
