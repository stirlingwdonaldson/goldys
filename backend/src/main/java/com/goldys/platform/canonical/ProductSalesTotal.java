package com.goldys.platform.canonical;

import java.math.BigDecimal;

/** An aggregated per-product sales total (summed across current canonical rows). */
public record ProductSalesTotal(
    String productNameKey, BigDecimal quantitySold, BigDecimal amount) {}
