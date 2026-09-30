package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** One source's per-product values for a trading date. */
public record ProductSourceTotal(
    String sourceSystem, BigDecimal quantitySold, BigDecimal amount, Instant recordedAt) {}
