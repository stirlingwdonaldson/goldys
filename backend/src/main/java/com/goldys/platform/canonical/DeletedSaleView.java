package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only view of one current deleted-sale fact, for modules outside the {@code canonical}
 * package.
 */
public record DeletedSaleView(LocalDate tradingDate, BigDecimal totalIncTax, BigDecimal totalTax) {}
