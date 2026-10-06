package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date/department's resolved labour metric. Cost is null while unknown. */
public record LabourMetric(
    LocalDate date,
    String department,
    BigDecimal scheduledHours,
    BigDecimal actualHours,
    BigDecimal scheduledCost,
    BigDecimal actualCost,
    String authoritativeSource,
    boolean hasConflict) {}
