package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Source-independent recipe observation; quantities and costs retain their signs. */
public record RecipeUsage(
    int lineId,
    int recipeId,
    String name,
    BigDecimal quantity,
    Integer unitId,
    String unit,
    BigDecimal originalPortionSize,
    Integer originalUnitId,
    String originalUnit,
    BigDecimal convertedQuantity,
    BigDecimal sourceCostPerPortion,
    BigDecimal cost,
    BigDecimal costIncludingLabour,
    Boolean brokenLink) {}
