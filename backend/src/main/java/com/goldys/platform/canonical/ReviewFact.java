package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.List;

/**
 * The public read view of a current canonical review fact, decoupled from the package-private
 * {@link CanonicalReview} entity so the {@code classification} and {@code semantic} modules can
 * consume review facts without seeing the persistence shape.
 */
public record ReviewFact(
    String reviewId,
    String source,
    Instant reviewedAt,
    Instant visitedAt,
    Integer overallRating,
    Integer foodRating,
    Integer serviceRating,
    Integer ambienceRating,
    Integer valueRating,
    String noiseLabel,
    Boolean wouldRecommend,
    String comment,
    String privateNote,
    List<String> tags,
    List<String> keywords,
    String guestName) {}
