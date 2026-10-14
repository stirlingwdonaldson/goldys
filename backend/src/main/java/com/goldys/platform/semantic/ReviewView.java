package com.goldys.platform.semantic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One resolved review as read by the analytics page, including its per-aspect sentiment.
 * Nullable fields: {@code overallRating}, {@code sentiment}, {@code predictedRating},
 * {@code comment}, {@code guestName}.
 */
public record ReviewView(
    String reviewId,
    String source,
    Instant reviewedAt,
    Integer overallRating,
    String sentiment,
    double sentimentConfidence,
    BigDecimal predictedRating,
    String comment,
    String guestName,
    List<AspectSentimentView> aspects) {}
