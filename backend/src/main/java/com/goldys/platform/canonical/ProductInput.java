package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A normalized Lightspeed product fact from the menu export, to be recorded as a canonical version.
 */
public record ProductInput(
    String sourceSystem,
    int productId,
    String productName,
    String category,
    BigDecimal sellPriceIncTax,
    boolean isModifier,
    String reportingGroup,
    UUID rawRecordId) {}
