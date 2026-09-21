package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A read-only view of one current per-product sales total. */
public record ProductSalesView(
    String sourceSystem,
    LocalDate tradingDate,
    String productNameKey,
    BigDecimal quantitySold,
    BigDecimal amount) {}
