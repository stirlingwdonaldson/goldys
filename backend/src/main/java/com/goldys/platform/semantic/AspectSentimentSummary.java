package com.goldys.platform.semantic;

/** Mentions and sentiment totals for one review aspect. */
public record AspectSentimentSummary(
    String aspect, long mentions, long positive, long neutral, long negative) {}
