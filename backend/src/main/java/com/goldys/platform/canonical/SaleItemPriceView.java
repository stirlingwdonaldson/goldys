package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A current sale item's name plus its sold price and cost, used by the recipe projector to enrich
 * each recipe with its Lightspeed price and actual cost.
 */
public record SaleItemPriceView(
    String itemName,
    BigDecimal soldPriceIncTax,
    BigDecimal costIncTax,
    LocalDate tradingDate,
    com.goldys.platform.semantic.RecipeEvidence evidence) {
  public SaleItemPriceView(
      String itemName,
      java.math.BigDecimal soldPriceIncTax,
      java.math.BigDecimal costIncTax,
      java.time.LocalDate tradingDate) {
    this(itemName, soldPriceIncTax, costIncTax, tradingDate, null);
  }
}
