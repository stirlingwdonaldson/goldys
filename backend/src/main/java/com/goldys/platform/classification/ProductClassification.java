package com.goldys.platform.classification;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The classifier's answer for one product: the chosen category, a probability per candidate
 * category keyed by category id, and the confidence assigned to the chosen category.
 */
public record ProductClassification(
    UUID categoryId, Map<UUID, BigDecimal> probabilities, BigDecimal confidence) {}
