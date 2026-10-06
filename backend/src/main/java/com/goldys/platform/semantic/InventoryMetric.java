package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date's resolved inventory metric. Wastage/stock-on-hand are null while unknown. */
public record InventoryMetric(
    LocalDate date,
    BigDecimal purchases,
    BigDecimal wastage,
    BigDecimal stockOnHand,
    String authoritativeSource,
    boolean hasConflict) {}
