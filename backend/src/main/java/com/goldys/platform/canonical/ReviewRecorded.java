package com.goldys.platform.canonical;

import java.util.List;

/**
 * Published after a canonical review fact is recorded, so the review classifier can classify the
 * new or corrected review in the same transaction.
 */
public record ReviewRecorded(
    String reviewId,
    String source,
    Integer overallRating,
    String comment,
    String privateNote,
    List<String> tags) {}
