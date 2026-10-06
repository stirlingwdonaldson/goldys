package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One product/day's resolved product-sales metric. {@code quantitySold}/{@code amount} are null
 * while the pair is unresolved.
 */
public record ProductSalesMetric(
    LocalDate tradingDate,
    String productName,
    BigDecimal quantitySold,
    BigDecimal amount,
    String authoritativeSource,
    boolean hasConflict) {}
