package com.goldys.platform.classification;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product's position in the 2-D embedding projection of the latest run, joined to its current
 * category assignment. {@code categoryId} and {@code confidence} are null while the product is
 * unassigned.
 */
public record ProductPoint(
    String productNameKey, double x, double y, UUID categoryId, BigDecimal confidence) {}
