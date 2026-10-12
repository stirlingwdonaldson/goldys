package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * A recipe's derived costing: cost per portion vs its Lightspeed menu price and actual cost. The
 * three derived figures are {@code null} when their inputs are missing — {@code margin = menuPrice
 * − costPerPortion}, {@code gpPercent = margin / menuPrice}, {@code costGap = actualCost −
 * costPerPortion}.
 */
public record RecipeCosting(
    int recipeId,
    String name,
    String folder,
    BigDecimal costPerPortion,
    BigDecimal actualCost,
    BigDecimal costGap,
    BigDecimal menuPrice,
    BigDecimal margin,
    BigDecimal gpPercent) {}
