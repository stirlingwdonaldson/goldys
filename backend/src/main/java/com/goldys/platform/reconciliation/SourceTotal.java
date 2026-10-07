package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's reported sales figures for a trading date. */
public record SourceTotal(
    String sourceSystem,
    BigDecimal totalSales,
    BigDecimal gstTotal,
    BigDecimal netTotal,
    Instant recordedAt) {}
