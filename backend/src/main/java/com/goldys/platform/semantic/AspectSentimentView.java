package com.goldys.platform.semantic;

/**
 * Per-aspect sentiment in a review read view. A semantic record rather than the
 * {@code classification.AspectSentiment} type so this leaf package has no classification dependency.
 */
public record AspectSentimentView(String aspect, String label, double confidence) {}
