package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recipe's derived costing: cost per portion vs its Lightspeed menu price and actual cost, plus
 * the raw yield ({@code portionSize}, which CTB may report as a count, a weight, or free text). The
 * three derived figures are {@code null} when their inputs are missing. When a dish is delisted and
 * has no current menu price or actual cost, the effective-price fallback applies: {@code margin =
 * (menuPrice ?? lastSoldPrice) − costPerPortion}, {@code gpPercent = margin / (menuPrice ??
 * lastSoldPrice)}, {@code costGap = (actualCost ?? lastSoldCost) − costPerPortion}.
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
    LocalDate lastSoldDate,
    java.time.Instant capturedAt,
    String costSource,
    String coverage,
    boolean stale,
    Integer folderId) {
  public RecipeCosting(
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
      LocalDate lastSoldDate) {
    this(
        recipeId,
        name,
        folder,
        portionSize,
        costPerPortion,
        actualCost,
        costGap,
        menuPrice,
        margin,
        gpPercent,
        menuState,
        priceSource,
        lastSoldPrice,
        lastSoldCost,
        lastSoldDate,
        null,
        "UNKNOWN",
        "UNKNOWN",
        false,
        null);
  }
}
