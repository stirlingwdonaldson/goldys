package com.goldys.platform.semantic;

/** Counts for one star rating (1..5), actual and predicted. */
public record RatingBucket(int stars, long given, long predicted) {}
