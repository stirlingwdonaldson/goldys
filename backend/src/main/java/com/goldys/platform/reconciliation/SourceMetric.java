package com.goldys.platform.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One source's value for the metric a rule is evaluated against, plus its system time. For daily
 * sales the metric is {@code totalSales}; for product sales it is {@code quantitySold}.
 */
public record SourceMetric(String sourceSystem, BigDecimal metric, Instant recordedAt) {}
