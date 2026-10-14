package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A normalized review fact from one source, to be recorded as a canonical version.
 *
 * <p>{@code guestId} is the source {@code Guest ID} string (null when the review carries none);
 * {@link CanonicalReview} derives the {@code canonical_review.guest_id} UUID from it.
 */
public record ReviewInput(
    String sourceSystem,
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
    String guestId,
    String guestName,
    UUID rawRecordId) {}
