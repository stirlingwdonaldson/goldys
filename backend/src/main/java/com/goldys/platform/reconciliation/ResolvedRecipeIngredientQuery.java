package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link RecipeIngredientMetricsQuery} backed by the resolved recipe-ingredient projection. */
@Service
public class ResolvedRecipeIngredientQuery implements RecipeIngredientMetricsQuery {
  private final ResolvedRecipeIngredientRepository repository;
  private final RecipeDefinitionReadAdapter definitions;

  public ResolvedRecipeIngredientQuery(ResolvedRecipeIngredientRepository repository) {
    this(repository, null);
  }

  @Autowired
  public ResolvedRecipeIngredientQuery(
      ResolvedRecipeIngredientRepository repository, RecipeDefinitionReadAdapter definitions) {
    this.repository = repository;
    this.definitions = definitions;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RecipeIngredient> ingredientsFor(int recipeId) {
    if (definitions != null) {
      var complete = definitions.ingredientsFor(recipeId);
      if (complete.isPresent()) return complete.get();
    }
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
        i.supplierName(),
        i.stockUomShortname(),
        i.stockUsable(),
        i.stockWastage(),
        i.isBrokenLink());
  }
}
