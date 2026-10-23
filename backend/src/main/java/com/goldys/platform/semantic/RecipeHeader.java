package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Source-independent recipe observation; quantities and costs retain their signs. */
public record RecipeHeader(
    int recipeId,
    String name,
    Integer folderId,
    Boolean active,
    Boolean locked,
    BigDecimal portionCount,
    BigDecimal portionSize,
    Integer unitId,
    String unit,
    BigDecimal totalCost,
    BigDecimal costPerPortion,
    BigDecimal costPerPortionIncludingLabour,
    BigDecimal labourCost,
    BigDecimal sellingPriceIncludingGst,
    BigDecimal revenueExcludingGst,
    BigDecimal gst,
    BigDecimal foodCostPercent,
    BigDecimal grossProfit,
    String description,
    String menuDescription,
    String hints,
    BigDecimal preparationMinutes,
    Boolean productionItem,
    Boolean countableOnStocktake,
    String videoUrl,
    String recipeDate,
    String createdByName,
    Integer difficultyRating,
    String barcode,
    Integer stockCategoryId,
    BigDecimal foodCostLimit,
    BigDecimal foodCostLimitIncludingLabour) {}
