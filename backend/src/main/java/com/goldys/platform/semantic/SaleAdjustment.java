package com.goldys.platform.semantic;

import java.math.BigDecimal;

/** Source-reported line revenue and adjustments; null means the report did not provide a field. */
public record SaleAdjustment(
    BigDecimal finalExTax,
    BigDecimal grossIncTax,
    BigDecimal grossExTax,
    BigDecimal amountIncTax,
    BigDecimal amountExTax,
    String type,
    String reason,
    String level,
    String promotion) {}
