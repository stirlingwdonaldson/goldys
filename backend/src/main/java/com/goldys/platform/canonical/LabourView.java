package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current labour fact, for modules outside the {@code canonical} package.
 */
public record LabourView(
    String sourceSystem,
    String department,
    LocalDate labourDate,
    BigDecimal scheduledHours,
    BigDecimal actualHours,
    BigDecimal scheduledCost,
    BigDecimal actualCost) {}
