package com.goldys.platform.canonical;

import java.math.BigDecimal;

/**
 * A read-only view of one current Lightspeed product fact, for modules outside the {@code
 * canonical} package.
 */
public record ProductView(
    int productId,
    String productName,
    String category,
    BigDecimal sellPriceIncTax,
    boolean isModifier,
    String reportingGroup) {}
