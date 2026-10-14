package com.goldys.platform.classification;

import java.math.BigDecimal;
import java.util.List;

/**
 * The classifier's answer for one review: the overall {@link Sentiment}, a predicted 1..5 rating
 * derived from the text (nullable — a review with no rating signal leaves it unset), and the sparse
 * per-aspect sentiments actually detected in the text.
 */
public record ReviewClassification(
    Sentiment sentiment, BigDecimal predictedRating, List<AspectSentiment> aspects) {}
