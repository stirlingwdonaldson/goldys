package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.util.List;

/** Reported CTB values remain separate from recomputed estimates and their limitations. */
public record RecipeComposition(
    RecipeDefinition definition,
    List<String> folderPath,
    List<RecipeOutletAllocation> outlets,
    BigDecimal sourceComponentTotal,
    BigDecimal calculatedTotal,
    BigDecimal difference,
    List<String> issues,
    boolean stale) {}
