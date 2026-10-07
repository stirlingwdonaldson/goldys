package com.goldys.platform.semantic.catalog;

import java.math.BigDecimal;

/**
 * One row in a ranked list (used by {@code product.top_sellers}). {@code hasConflict} mirrors the
 * source row's unresolved flag so consumers can render an "unresolved" badge.
 */
public record MetricRankedItem(
    String label, BigDecimal primary, BigDecimal secondary, boolean hasConflict) {}
