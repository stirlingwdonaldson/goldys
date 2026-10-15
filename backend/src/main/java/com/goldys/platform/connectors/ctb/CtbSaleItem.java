package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/**
 * One per-product sale-item row from CTB's {@code Sale/SearchSaleItemsByDateRange}, including the
 * source-native measures (cost, food cost, discount, tax, unit price, profit) that the universal
 * product fact drops, plus the source grouping key that distinguishes otherwise-identical rows.
 */
public record CtbSaleItem(
    String stockCode,
    String stockDescription,
    BigDecimal quantitySold,
    BigDecimal amount,
    BigDecimal costPerUnit,
    BigDecimal foodCost,
    BigDecimal totalFoodCost,
    BigDecimal profit,
    BigDecimal discountedAmount,
    BigDecimal taxAmount,
    BigDecimal unitPrice,
    String uniqueGroupId) {}
