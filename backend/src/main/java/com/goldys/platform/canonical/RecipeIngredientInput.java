package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A normalized recipe ingredient-line observation from one source. {@code parentRecipeId} is the
 * recipe the payload was fetched for; {@code lineRecipeId}/{@code lineRecipeName} describe the
 * recipe the line itself belongs to (the parent for main lines, a sub-recipe for {@code
 * isSubRecipe} lines). Money/quantity values are {@code null} when the source sent nothing
 * parseable.
 */
public record RecipeIngredientInput(
    String sourceSystem,
    int parentRecipeId,
    Integer lineRecipeId,
    String lineRecipeName,
    Boolean isSubRecipe,
    int recipeDetailId,
    Integer stockId,
    String stockCode,
    String stockDescription,
    String ingredientDescription,
    String supplierName,
    String supplierStockCode,
    BigDecimal portionWeight,
    String unitOfMeasurement,
    String stockUomShortname,
    BigDecimal costPerQty,
    BigDecimal costPerUnit,
    BigDecimal stockUsable,
    BigDecimal stockWastage,
    Boolean isBrokenLink,
    UUID rawRecordId) {}
