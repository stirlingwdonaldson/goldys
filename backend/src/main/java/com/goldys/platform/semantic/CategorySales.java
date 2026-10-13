package com.goldys.platform.semantic;

import java.math.BigDecimal;

/**
 * One category's resolved sales. {@code group} is null for flat categories that have no parent
 * group.
 */
public record CategorySales(
    String group, String category, BigDecimal quantity, BigDecimal amount) {}
