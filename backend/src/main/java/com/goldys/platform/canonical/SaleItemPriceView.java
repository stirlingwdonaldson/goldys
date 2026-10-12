package com.goldys.platform.canonical;

import java.math.BigDecimal;

/**
 * A current sale item's name plus its sold price and cost, used by the recipe projector to enrich
 * each recipe with its Lightspeed price and actual cost.
 */
public record SaleItemPriceView(
    String itemName, BigDecimal soldPriceIncTax, BigDecimal costIncTax) {}
