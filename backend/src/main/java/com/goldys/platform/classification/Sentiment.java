package com.goldys.platform.classification;

import java.math.BigDecimal;

/**
 * An overall sentiment verdict: a label ({@code POSITIVE}/{@code NEUTRAL}/{@code NEGATIVE}) and a
 * confidence in {@code [0, 1]}.
 */
public record Sentiment(String label, BigDecimal confidence) {}
