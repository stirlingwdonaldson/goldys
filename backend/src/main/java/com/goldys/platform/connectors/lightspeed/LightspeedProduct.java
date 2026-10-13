package com.goldys.platform.connectors.lightspeed;

import java.math.BigDecimal;

/**
 * One product row parsed from the Lightspeed product-catalog CSV drop (the menu export's product
 * table). This is the raw, typed row — canonicalization to {@code CanonicalProduct} happens in the
 * ingest service.
 *
 * @param productId the Lightspeed {@code ProductID} column
 * @param productName the {@code ProductName} column (kept verbatim, including any price prefix)
 * @param category the {@code CategoryNames} column, or {@code null} when blank
 * @param sellPriceIncTax the {@code SellPriceIncTax} column with currency/grouping stripped, or
 *     {@code null} when blank
 * @param isModifier whether the {@code IsModifier} column is {@code "1"}
 * @param reportingGroup the {@code ReportingGroup} column, or {@code null} when blank
 */
public record LightspeedProduct(
    int productId,
    String productName,
    String category,
    BigDecimal sellPriceIncTax,
    boolean isModifier,
    String reportingGroup) {}
