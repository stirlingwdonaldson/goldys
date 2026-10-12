package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link RecipeIngredientMetricsQuery} backed by the resolved recipe-ingredient projection. */
@Service
public class ResolvedRecipeIngredientQuery implements RecipeIngredientMetricsQuery {
  private final ResolvedRecipeIngredientRepository repository;

  public ResolvedRecipeIngredientQuery(ResolvedRecipeIngredientRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<RecipeIngredient> ingredientsFor(int recipeId) {
    return repository.findByParentRecipeId(recipeId).stream()
        .map(ResolvedRecipeIngredientQuery::toRecord)
        .toList();
  }

  private static RecipeIngredient toRecord(ResolvedRecipeIngredient i) {
    return new RecipeIngredient(
        i.parentRecipeId(),
        i.lineRecipeId(),
        i.lineRecipeName(),
        i.isSubRecipe(),
        i.recipeDetailId(),
        i.stockId(),
        i.stockCode(),
        i.stockDescription(),
        i.portionWeight(),
        i.unitOfMeasurement(),
        i.costPerQty(),
        i.costPerUnit(),
        i.supplierName());
  }
}
