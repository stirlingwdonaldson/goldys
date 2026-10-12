package com.goldys.platform.canonical;

import java.math.BigDecimal;

/**
 * A read-only view of one current recipe-ingredient line, for modules outside the {@code canonical}
 * package (the projector rebuilds {@code resolved_recipe_ingredient} from these).
 */
public record RecipeIngredientView(
    int parentRecipeId,
    Integer lineRecipeId,
    String lineRecipeName,
    Boolean isSubRecipe,
    int recipeDetailId,
    Integer stockId,
    String stockCode,
    String stockDescription,
    BigDecimal portionWeight,
    String unitOfMeasurement,
    BigDecimal costPerQty,
    BigDecimal costPerUnit,
    String supplierName) {}
