package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.UUID;

/** A normalized recipe-cost fact from one source, to be recorded as a canonical version. */
public record RecipeCostInput(
    String sourceSystem,
    int recipeId,
    String recipeName,
    BigDecimal costPerPortion,
    BigDecimal costPerPortionInclLabour,
    BigDecimal totalCost,
    String portionSize,
    BigDecimal portionSizeValue,
    UUID rawRecordId) {}
