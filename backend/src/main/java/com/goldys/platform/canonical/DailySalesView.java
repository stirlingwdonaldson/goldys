package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current daily sales total, for modules outside the {@code canonical}
 * package.
 */
public record DailySalesView(
    String sourceSystem,
    LocalDate tradingDate,
    BigDecimal totalSales,
    BigDecimal gstTotal,
    BigDecimal netTotal) {}
