package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReviewQuery;
import com.goldys.platform.canonical.ReviewFact;
import com.goldys.platform.semantic.AspectSentimentSummary;
import com.goldys.platform.semantic.AspectSentimentView;
import com.goldys.platform.semantic.RatingBucket;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewOverview;
import com.goldys.platform.semantic.ReviewView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** In-memory join coverage for {@link ResolvedReviewMetricsQuery}: canonical + classification. */
class ResolvedReviewMetricsQueryTest {

  private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);

  private final CanonicalReviewQuery reviews = mock(CanonicalReviewQuery.class);
  private final ReviewClassificationRepository classifications =
      mock(ReviewClassificationRepository.class);

  private final ResolvedReviewMetricsQuery query =
      new ResolvedReviewMetricsQuery(reviews, classifications, "UTC");

  @Test
  void overview_computes_average_and_split() {
    ReviewFact positive = fact("OT-1", "OpenTable", 5);
    ReviewFact negative = fact("RT-1", "Google", null); // null overall rating (Review Focus #5)

    when(reviews.currentReviews(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(positive, negative));
    when(classifications.findAll())
        .thenReturn(
            List.of(
                classification("OT-1", "POSITIVE", "4.5", List.of()),
                classification("RT-1", "NEGATIVE", "2.0", List.of())));

    ReviewOverview overview = query.overview(FROM, TO);

    assertThat(overview.total()).isEqualTo(2);
    assertThat(overview.averageRating()).isEqualTo(5.0);
    assertThat(overview.positive()).isEqualTo(1);
    assertThat(overview.neutral()).isEqualTo(0);
    assertThat(overview.negative()).isEqualTo(1);
  }

  @Test
  void rating_distribution_counts_predicted_and_given() {
    ReviewFact google = fact("RT-1", "Google", null); // null given rating (Review Focus #2)

    when(reviews.currentReviews(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(google));
    when(classifications.findAll())
        .thenReturn(List.of(classification("RT-1", "POSITIVE", "4.0", List.of())));

    List<RatingBucket> buckets = query.ratingDistribution(FROM, TO);

    assertThat(bucket(buckets, 4).given()).isEqualTo(0);
    assertThat(bucket(buckets, 4).predicted()).isEqualTo(1);
    assertThat(buckets).extracting(RatingBucket::given).containsOnly(0L);
  }

  @Test
  void aspect_breakdown_rolls_up_sentiment() {
    ReviewFact one = fact("OT-1", "OpenTable", 5);
    ReviewFact two = fact("OT-2", "OpenTable", 3);

    when(reviews.currentReviews(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(one, two));
    when(classifications.findAll())
        .thenReturn(
            List.of(
                classification(
                    "OT-1",
                    "POSITIVE",
                    "4.5",
                    List.of(new AspectSentiment("service", "POSITIVE", new BigDecimal("0.9")))),
                classification(
                    "OT-2",
                    "NEGATIVE",
                    "2.0",
                    List.of(new AspectSentiment("service", "NEGATIVE", new BigDecimal("0.8"))))));

    List<AspectSentimentSummary> summaries = query.aspectBreakdown(FROM, TO);

    assertThat(summaries).containsExactly(new AspectSentimentSummary("service", 2, 1, 0, 1));
  }

  @Test
  void reviews_filters_by_aspect() {
    ReviewFact serviceAndFood = fact("OT-1", "OpenTable", 5);
    ReviewFact serviceOnly = fact("OT-2", "OpenTable", 4);
    ReviewFact foodOnly = fact("OT-3", "Google", null);

    when(reviews.allCurrentReviews()).thenReturn(List.of(serviceAndFood, serviceOnly, foodOnly));
    when(classifications.findAll())
        .thenReturn(
            List.of(
                classification(
                    "OT-1",
                    "POSITIVE",
                    "4.5",
                    List.of(
                        new AspectSentiment("service", "POSITIVE", new BigDecimal("0.9")),
                        new AspectSentiment("food", "POSITIVE", new BigDecimal("0.95")))),
                classification(
                    "OT-2",
                    "NEUTRAL",
                    "3.0",
                    List.of(new AspectSentiment("service", "NEUTRAL", new BigDecimal("0.6")))),
                classification(
                    "OT-3",
                    "NEGATIVE",
                    "2.0",
                    List.of(new AspectSentiment("food", "NEGATIVE", new BigDecimal("0.85"))))));

    List<ReviewView> result =
        query.reviews(new ReviewFilter(null, null, null, null, "service", null, null));

    assertThat(result).extracting(ReviewView::reviewId).containsExactly("OT-1", "OT-2");
    assertThat(result.get(0).aspects())
        .containsExactly(
            new AspectSentimentView("service", "POSITIVE", 0.9),
            new AspectSentimentView("food", "POSITIVE", 0.95));
  }

  @Test
  void reviews_populates_sub_ratings() {
    ReviewFact fact =
        new ReviewFact(
            "OT-1",
            "OpenTable",
            NOW,
            NOW,
            4,
            5,
            3,
            4,
            5,
            null,
            null,
            "Great",
            null,
            List.of(),
            List.of(),
            "Ada");

    when(reviews.allCurrentReviews()).thenReturn(List.of(fact));
    when(classifications.findAll())
        .thenReturn(List.of(classification("OT-1", "POSITIVE", "4.5", List.of())));

    List<ReviewView> result =
        query.reviews(new ReviewFilter(null, null, null, null, null, null, null));

    assertThat(result).hasSize(1);
    ReviewView view = result.get(0);
    assertThat(view.overallRating()).isEqualTo(4);
    assertThat(view.foodRating()).isEqualTo(5);
    assertThat(view.serviceRating()).isEqualTo(3);
    assertThat(view.ambienceRating()).isEqualTo(4);
    assertThat(view.valueRating()).isEqualTo(5);
  }

  private static RatingBucket bucket(List<RatingBucket> buckets, int stars) {
    return buckets.stream().filter(b -> b.stars() == stars).findFirst().orElseThrow();
  }

  private static ReviewFact fact(String reviewId, String source, Integer overallRating) {
    return new ReviewFact(
        reviewId,
        source,
        NOW,
        NOW,
        overallRating,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        null);
  }

  private static ReviewClassificationEntity classification(
      String reviewId, String sentiment, String predictedRating, List<AspectSentiment> aspects) {
    return ReviewClassificationEntity.create(
        reviewId,
        new ReviewClassification(
            new Sentiment(sentiment, new BigDecimal("0.90")),
            predictedRating == null ? null : new BigDecimal(predictedRating),
            aspects),
        NOW);
  }
}
