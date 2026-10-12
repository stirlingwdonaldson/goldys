package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * One recipe's resolved facts: its CTB recipe-level cost plus its Lightspeed menu price and actual
 * cost (matched by normalized name in the projector).
 */
public record RecipeSummary(
    int recipeId,
    String name,
    String folder,
    BigDecimal costPerPortion,
    BigDecimal costPerPortionInclLabour,
    BigDecimal totalCost,
    String portionSize,
    BigDecimal portionSizeValue,
    BigDecimal menuPrice,
    BigDecimal actualCost) {}
