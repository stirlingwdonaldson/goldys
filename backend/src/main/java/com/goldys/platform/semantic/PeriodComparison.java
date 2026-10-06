package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * Total covers for two periods plus the percentage delta of B relative to A. {@code
 * coversDeltaPercent} is {@code null} when {@code coversA} is zero.
 */
public record PeriodComparison(long coversA, long coversB, BigDecimal coversDeltaPercent) {}
