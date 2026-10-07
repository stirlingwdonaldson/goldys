package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading date's resolved daily-sales metric. {@code grossSales}/{@code netSales}/{@code gst}
 * are null while unresolved.
 */
public record DailySalesMetric(
    LocalDate tradingDate,
    BigDecimal grossSales,
    BigDecimal netSales,
    BigDecimal gst,
    String authoritativeSource,
    boolean hasConflict) {}
