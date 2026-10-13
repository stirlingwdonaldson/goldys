package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recipe's derived costing: cost per portion vs its Lightspeed menu price and actual cost, plus
 * the raw yield ({@code portionSize}, which CTB may report as a count, a weight, or free text). The
 * three derived figures are {@code null} when their inputs are missing — {@code margin = menuPrice
 * − costPerPortion}, {@code gpPercent = margin / menuPrice}, {@code costGap = actualCost −
 * costPerPortion}.
 */
public record RecipeCosting(
    int recipeId,
    String name,
    String folder,
    String portionSize,
    BigDecimal costPerPortion,
    BigDecimal actualCost,
    BigDecimal costGap,
    BigDecimal menuPrice,
    BigDecimal margin,
    BigDecimal gpPercent,
    String menuState,
    String priceSource,
    BigDecimal lastSoldPrice,
    BigDecimal lastSoldCost,
    LocalDate lastSoldDate) {}
