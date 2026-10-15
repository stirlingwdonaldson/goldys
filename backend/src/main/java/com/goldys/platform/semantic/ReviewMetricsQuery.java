package com.goldys.platform.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reads over resolved guest reviews. Implementations read the resolved projection;
 * consumers never derive these figures from canonical source rows or the classification package.
 */
public interface ReviewMetricsQuery {

  /** Headline counts over the inclusive range. */
  ReviewOverview overview(LocalDate from, LocalDate to);

  /** Weekly sentiment buckets over the inclusive range. */
  List<SentimentBucket> sentimentTrend(LocalDate from, LocalDate to);

  /** Weekly average rating and % who would recommend over the inclusive range. */
  List<RatingTrendBucket> ratingTrend(LocalDate from, LocalDate to);

  /** Mentions and sentiment per aspect over the inclusive range. */
  List<AspectSentimentSummary> aspectBreakdown(LocalDate from, LocalDate to);

  /** Star ratings, actual and predicted, over the inclusive range. */
  List<RatingBucket> ratingDistribution(LocalDate from, LocalDate to);

  /** Per-source comparison over the inclusive range. */
  List<SourceSummary> sourceComparison(LocalDate from, LocalDate to);

  /** Individual reviews matching the filter. */
  List<ReviewView> reviews(ReviewFilter filter);
}
