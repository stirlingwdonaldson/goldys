package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Current recipe observations, not historical actual COGS. Ratios identify the configured tax-basis
 * assumption.
 */
public record SaleCosting(
    List<Line> lines,
    boolean complete,
    BigDecimal recipeCost,
    BigDecimal grossProfit,
    BigDecimal margin,
    String qualification,
    String recipeCostTaxBasis) {
  public record Line(
      String receiptLineId,
      String itemName,
      BigDecimal quantity,
      BigDecimal revenueIncTax,
      BigDecimal revenueExTax,
      Integer recipeId,
      String recipeName,
      String matchMethod,
      BigDecimal costPerPortion,
      BigDecimal recipeCost,
      Instant costObservedAt,
      String costSource,
      String status) {}
}
