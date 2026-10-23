package com.goldys.platform.canonical;

import java.math.BigDecimal;

/**
 * A read-only view of one current recipe-cost fact, for modules outside the {@code canonical}
 * package.
 */
public record RecipeCostView(
    int recipeId,
    String recipeName,
    BigDecimal costPerPortion,
    BigDecimal costPerPortionInclLabour,
    BigDecimal totalCost,
    String portionSize,
    BigDecimal portionSizeValue,
    com.goldys.platform.semantic.RecipeEvidence evidence) {
  public RecipeCostView(
      int recipeId,
      String recipeName,
      java.math.BigDecimal costPerPortion,
      java.math.BigDecimal costPerPortionInclLabour,
      java.math.BigDecimal totalCost,
      String portionSize,
      java.math.BigDecimal portionSizeValue) {
    this(
        recipeId,
        recipeName,
        costPerPortion,
        costPerPortionInclLabour,
        totalCost,
        portionSize,
        portionSizeValue,
        null);
  }
}
