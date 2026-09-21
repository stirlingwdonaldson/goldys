package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** One per-product sale-item row from CTB's {@code Sale/SearchSaleItemsByDateRange}. */
public record CtbSaleItem(
    String stockCode, String stockDescription, BigDecimal quantitySold, BigDecimal amount) {}
