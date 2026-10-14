package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/**
 * One per-product sale-item row from CTB's {@code Sale/SearchSaleItemsByDateRange}, including the
 * source-native measures (cost, food cost, discount, tax, unit price) that the universal product
 * fact drops.
 */
public record CtbSaleItem(
    String stockCode,
    String stockDescription,
    BigDecimal quantitySold,
    BigDecimal amount,
    BigDecimal costPerUnit,
    BigDecimal totalFoodCost,
    BigDecimal discountedAmount,
    BigDecimal taxAmount,
    BigDecimal unitPrice) {}
