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
    BigDecimal portionSizeValue) {}
