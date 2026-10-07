package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;

/** One row in a ranked list (used by {@code product.top_sellers}). */
public record MetricRankedItem(String label, BigDecimal primary, BigDecimal secondary) {}
