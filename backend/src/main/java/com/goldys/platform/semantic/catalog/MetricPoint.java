package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bucket's value. {@code value} is null when the bucket has no resolved data. */
public record MetricPoint(LocalDate bucketStart, BigDecimal value) {}
