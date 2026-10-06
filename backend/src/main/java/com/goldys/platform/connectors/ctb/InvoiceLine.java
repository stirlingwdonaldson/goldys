package com.goldys.platform.connectors.ctb;

import java.math.BigDecimal;

/** A parsed invoice line item (product, qty, unit cost, line total). */
public record InvoiceLine(
    String productNameKey, BigDecimal quantity, BigDecimal unitCost, BigDecimal lineTotal) {}
