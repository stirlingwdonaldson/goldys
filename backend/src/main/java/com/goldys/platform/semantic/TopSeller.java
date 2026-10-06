package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * A top-selling product over a period. {@code quantitySold}/{@code amount} sum resolved rows only.
 */
public record TopSeller(
    String productName, BigDecimal quantitySold, BigDecimal amount, boolean hasConflict) {}
