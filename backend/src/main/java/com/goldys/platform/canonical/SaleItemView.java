package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current sale-item fact, for modules outside the {@code canonical}
 * package.
 */
public record SaleItemView(
    LocalDate tradingDate, String categoryName, BigDecimal quantitySold, BigDecimal amount) {}
