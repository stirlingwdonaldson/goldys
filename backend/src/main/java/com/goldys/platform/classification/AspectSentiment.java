package com.goldys.platform.classification;

import java.math.BigDecimal;

/**
 * The sentiment detected for one aspect of a review: the aspect key (lowercase taxonomy, e.g.
 * {@code service}), the sentiment label, and its confidence in {@code [0, 1]}.
 */
public record AspectSentiment(String aspect, String label, BigDecimal confidence) {}
