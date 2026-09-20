package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The resolved total for a trading date. {@code resolvedTotal} is null while a conflict is
 * unresolved; {@code authoritativeSource} is the override source, {@code "agreed"}, or null.
 */
public record DailySalesResolved(
    LocalDate tradingDate, BigDecimal resolvedTotal, String authoritativeSource) {}
