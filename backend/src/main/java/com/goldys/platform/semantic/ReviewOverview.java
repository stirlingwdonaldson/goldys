package com.goldys.platform.semantic;

/** Headline review metrics over a range. */
public record ReviewOverview(
    long total,
    double averageRating,
    long positive,
    long neutral,
    long negative,
    double wouldRecommendPct) {}
