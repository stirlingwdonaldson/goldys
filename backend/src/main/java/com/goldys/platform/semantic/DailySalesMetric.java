package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One trading date's resolved daily-sales metric. {@code grossSales} is null while unresolved. */
public record DailySalesMetric(
    LocalDate tradingDate,
    BigDecimal grossSales,
    String authoritativeSource,
    boolean hasConflict) {}
