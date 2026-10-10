package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One date/category's resolved sale-item mix: quantity and amount. */
public record SaleItemMix(
    LocalDate tradingDate,
    String categoryName,
    BigDecimal quantity,
    BigDecimal amount,
    boolean hasConflict) {}
