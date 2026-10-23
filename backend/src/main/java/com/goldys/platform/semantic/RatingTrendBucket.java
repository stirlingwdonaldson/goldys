package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One weekly bucket of average rating and the share of reviewers who would recommend. {@code
 * avgRating} (0–5) and {@code recommendPercent} (0–100) are {@code null} when the bucket has no
 * ratings / no answers respectively.
 */
public record RatingTrendBucket(
    LocalDate periodStart, BigDecimal avgRating, BigDecimal recommendPercent) {}
