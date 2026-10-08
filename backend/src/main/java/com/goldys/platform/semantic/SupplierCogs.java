package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * Purchases (COGS) and Wine Equalisation Tax grouped by supplier over a period, so liquor-tax
 * accounting and per-supplier spend are visible without reading canonical source rows.
 */
public record SupplierCogs(String supplier, BigDecimal lineTotal, BigDecimal wetAmount) {}
