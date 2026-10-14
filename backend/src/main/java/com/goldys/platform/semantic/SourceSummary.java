package com.goldys.platform.semantic;

/** Aggregated review figures for one source system. */
public record SourceSummary(
    String source, long total, double averageRating, long positive, long negative) {}
