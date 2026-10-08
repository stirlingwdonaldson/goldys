package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** A parsed invoice line from CTB's Custom Invoice Export CSV (raw fields only). */
public record CtInvoiceLine(
    String stockCode, // StockCode
    String description, // StockDescription
    String rawQuantity, // LineQuantity (coarse; COGS uses lineTotalExTax, not this)
    BigDecimal unitCostExTax, // LineUnitCostExTax
    BigDecimal lineTotalExTax) {} // LineTotalExTax — the COGS driver
