package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's reported total for a trading date. */
public record SourceTotal(String sourceSystem, BigDecimal totalSales, Instant recordedAt) {}
