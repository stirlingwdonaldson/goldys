package com.goldys.platform.canonical;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical recipe ingredients, so modules outside this package never
 * touch the package-private entity or repository directly.
 */
@Service
public class CanonicalRecipeIngredientQuery {
  private final CanonicalRecipeIngredientRepository repository;

  public CanonicalRecipeIngredientQuery(CanonicalRecipeIngredientRepository repository) {
    this.repository = repository;
  }

  /** All current recipe-ingredient lines, mapped to views. */
  public List<RecipeIngredientView> currentIngredients() {
    return repository.findAllCurrent().stream().map(this::toView).toList();
  }

  private RecipeIngredientView toView(CanonicalRecipeIngredient i) {
    return new RecipeIngredientView(
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
