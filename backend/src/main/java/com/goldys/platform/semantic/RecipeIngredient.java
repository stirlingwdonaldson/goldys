package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * One recipe's resolved ingredient line: its raw quantity and unit, CTB cost figures, supplier, and
 * the sub-recipe flag. {@code lineRecipeName} is the recipe the line belongs to (the parent for
 * main lines, a sub-recipe for sub-recipe lines); {@code stockDescription} is the ingredient's
 * name. Quantity and cost are {@code null} when CTB sent nothing parseable — never a fabricated
 * zero.
 */
public record RecipeIngredient(
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
