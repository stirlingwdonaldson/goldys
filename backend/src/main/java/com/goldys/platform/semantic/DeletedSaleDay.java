package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date's resolved deleted-sale totals. */
public record DeletedSaleDay(
    LocalDate tradingDate,
    long count,
    BigDecimal totalIncTax,
    BigDecimal totalTax,
    boolean hasConflict) {}
