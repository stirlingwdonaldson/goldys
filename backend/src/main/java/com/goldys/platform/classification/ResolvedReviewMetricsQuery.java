package com.goldys.platform.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalReviewQuery;
import com.goldys.platform.canonical.ReviewFact;
import com.goldys.platform.semantic.AspectSentimentSummary;
import com.goldys.platform.semantic.AspectSentimentView;
import com.goldys.platform.semantic.RatingBucket;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewMetricsQuery;
import com.goldys.platform.semantic.ReviewOverview;
import com.goldys.platform.semantic.ReviewView;
import com.goldys.platform.semantic.SentimentBucket;
import com.goldys.platform.semantic.SourceSummary;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link ReviewMetricsQuery} backed by the resolved read direction: current {@code
 * canonical_review} facts (via {@link CanonicalReviewQuery}) joined in memory to {@code
 * review_classification} (via {@link ReviewClassificationRepository}), at pub scale (~1,578
 * reviews). The venue zone turns the caller's {@link LocalDate} range into the instants the
 * canonical query keys on, and buckets the weekly trend.
 */
@Service
public class ResolvedReviewMetricsQuery implements ReviewMetricsQuery {

  private static final String POSITIVE = "POSITIVE";
  private static final String NEUTRAL = "NEUTRAL";
  private static final String NEGATIVE = "NEGATIVE";

  /** Same plain mapper the {@link ReviewClassificationEntity} writes {@code aspectsJson} with. */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CanonicalReviewQuery reviews;
  private final ReviewClassificationRepository classifications;
  private final ZoneId zone;

  public ResolvedReviewMetricsQuery(
      CanonicalReviewQuery reviews,
      ReviewClassificationRepository classifications,
      @Value("${app.venue.timezone:Australia/Melbourne}") String zone) {
    this.reviews = reviews;
    this.classifications = classifications;
    this.zone = ZoneId.of(zone);
  }

  @Override
  public ReviewOverview overview(LocalDate from, LocalDate to) {
    List<ReviewFact> facts = reviews.currentReviews(start(from), end(to));
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    long total = facts.size();
    long ratingSum = 0;
    long ratingCount = 0;
    long positive = 0;
    long neutral = 0;
    long negative = 0;
    long recommend = 0;
    long recommendKnown = 0;

    for (ReviewFact fact : facts) {
      if (fact.overallRating() != null) {
        ratingSum += fact.overallRating();
        ratingCount++;
      }
      String sentiment = sentimentOf(byReview.get(fact.reviewId()));
      if (POSITIVE.equals(sentiment)) {
        positive++;
      } else if (NEUTRAL.equals(sentiment)) {
        neutral++;
      } else if (NEGATIVE.equals(sentiment)) {
        negative++;
      }
      if (fact.wouldRecommend() != null) {
        recommendKnown++;
        if (fact.wouldRecommend()) {
          recommend++;
        }
      }
    }

    double averageRating = ratingCount == 0 ? 0.0 : (double) ratingSum / ratingCount;
    double wouldRecommendPct = recommendKnown == 0 ? 0.0 : 100.0 * recommend / recommendKnown;
    return new ReviewOverview(total, averageRating, positive, neutral, negative, wouldRecommendPct);
  }

  @Override
  public List<SentimentBucket> sentimentTrend(LocalDate from, LocalDate to) {
    List<ReviewFact> facts = reviews.currentReviews(start(from), end(to));
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    // Week start (ISO Monday) -> [positive, neutral, negative].
    Map<LocalDate, long[]> byWeek = new TreeMap<>();
    for (ReviewFact fact : facts) {
      LocalDate day = fact.reviewedAt().atZone(zone).toLocalDate();
      LocalDate week = day.minusDays(day.getDayOfWeek().getValue() - 1);
      long[] bucket = byWeek.computeIfAbsent(week, k -> new long[3]);
      incrementSentiment(bucket, sentimentOf(byReview.get(fact.reviewId())));
    }

    List<SentimentBucket> out = new ArrayList<>(byWeek.size());
    for (Map.Entry<LocalDate, long[]> entry : byWeek.entrySet()) {
      long[] b = entry.getValue();
      out.add(new SentimentBucket(entry.getKey(), b[0], b[1], b[2]));
    }
    return out;
  }

  @Override
  public List<AspectSentimentSummary> aspectBreakdown(LocalDate from, LocalDate to) {
    List<ReviewFact> facts = reviews.currentReviews(start(from), end(to));
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    // Aspect key -> [mentions, positive, neutral, negative].
    Map<String, long[]> byAspect = new TreeMap<>();
    for (ReviewFact fact : facts) {
      ReviewClassificationEntity entity = byReview.get(fact.reviewId());
      if (entity == null) {
        continue;
      }
      for (AspectSentiment aspect : parseAspects(entity.aspectsJson())) {
        long[] b = byAspect.computeIfAbsent(aspect.aspect(), k -> new long[4]);
        b[0]++;
        incrementSentiment(b, 1, aspect.label());
      }
    }

    List<AspectSentimentSummary> out = new ArrayList<>(byAspect.size());
    for (Map.Entry<String, long[]> entry : byAspect.entrySet()) {
      long[] b = entry.getValue();
      out.add(new AspectSentimentSummary(entry.getKey(), b[0], b[1], b[2], b[3]));
    }
    return out;
  }

  @Override
  public List<RatingBucket> ratingDistribution(LocalDate from, LocalDate to) {
    List<ReviewFact> facts = reviews.currentReviews(start(from), end(to));
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    long[] given = new long[6];
    long[] predicted = new long[6];
    for (ReviewFact fact : facts) {
      if (fact.overallRating() != null && fact.overallRating() >= 1 && fact.overallRating() <= 5) {
        given[fact.overallRating()]++;
      }
      ReviewClassificationEntity entity = byReview.get(fact.reviewId());
      if (entity != null && entity.predictedRating() != null) {
        int stars = entity.predictedRating().setScale(0, RoundingMode.HALF_UP).intValue();
        if (stars >= 1 && stars <= 5) {
          predicted[stars]++;
        }
      }
    }

    List<RatingBucket> out = new ArrayList<>(5);
    for (int stars = 1; stars <= 5; stars++) {
      out.add(new RatingBucket(stars, given[stars], predicted[stars]));
    }
    return out;
  }

  @Override
  public List<SourceSummary> sourceComparison(LocalDate from, LocalDate to) {
    List<ReviewFact> facts = reviews.currentReviews(start(from), end(to));
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    // Source -> [total, ratingSum, ratingCount, positive, negative].
    Map<String, long[]> bySource = new TreeMap<>();
    for (ReviewFact fact : facts) {
      long[] b = bySource.computeIfAbsent(fact.source(), k -> new long[5]);
      b[0]++;
      if (fact.overallRating() != null) {
        b[1] += fact.overallRating();
        b[2]++;
      }
      String sentiment = sentimentOf(byReview.get(fact.reviewId()));
      if (POSITIVE.equals(sentiment)) {
        b[3]++;
      } else if (NEGATIVE.equals(sentiment)) {
        b[4]++;
      }
    }

    List<SourceSummary> out = new ArrayList<>(bySource.size());
    for (Map.Entry<String, long[]> entry : bySource.entrySet()) {
      long[] b = entry.getValue();
      double averageRating = b[2] == 0 ? 0.0 : (double) b[1] / b[2];
      out.add(new SourceSummary(entry.getKey(), b[0], averageRating, b[3], b[4]));
    }
    return out;
  }

  @Override
  public List<ReviewView> reviews(ReviewFilter filter) {
    List<ReviewFact> facts = reviews.allCurrentReviews();
    Map<String, ReviewClassificationEntity> byReview = classificationById();

    List<ReviewView> out = new ArrayList<>();
    for (ReviewFact fact : facts) {
      ReviewClassificationEntity entity = byReview.get(fact.reviewId());
      List<AspectSentiment> aspects =
          entity == null ? List.of() : parseAspects(entity.aspectsJson());

      if (!matchesDate(fact, filter)
          || !matchesSource(fact, filter)
          || !matchesSentiment(entity, filter)
          || !matchesAspect(aspects, filter)
          || !matchesMinRating(fact, filter)
          || !matchesQuery(fact, filter)) {
        continue;
      }
      out.add(toView(fact, entity, aspects));
    }
    return out;
  }

  private ReviewView toView(
      ReviewFact fact, ReviewClassificationEntity entity, List<AspectSentiment> aspects) {
    List<AspectSentimentView> views =
        aspects.stream()
            .map(
                a ->
                    new AspectSentimentView(
                        a.aspect(),
                        a.label(),
                        a.confidence() == null ? 0.0 : a.confidence().doubleValue()))
            .toList();
    return new ReviewView(
        fact.reviewId(),
        fact.source(),
        fact.reviewedAt(),
        fact.overallRating(),
        fact.foodRating(),
        fact.serviceRating(),
        fact.ambienceRating(),
        fact.valueRating(),
        sentimentOf(entity),
        entity == null ? 0.0 : entity.sentimentConfidence().doubleValue(),
        entity == null ? null : entity.predictedRating(),
        fact.comment(),
        fact.guestName(),
        views);
  }

  private boolean matchesDate(ReviewFact fact, ReviewFilter filter) {
    if (filter.from() == null && filter.to() == null) {
      return true;
    }
    LocalDate day = fact.reviewedAt().atZone(zone).toLocalDate();
    if (filter.from() != null && day.isBefore(filter.from())) {
      return false;
    }
    return filter.to() == null || !day.isAfter(filter.to());
  }

  private static boolean matchesSource(ReviewFact fact, ReviewFilter filter) {
    return filter.source() == null || filter.source().equals(fact.source());
  }

  private static boolean matchesSentiment(ReviewClassificationEntity entity, ReviewFilter filter) {
    return filter.sentiment() == null || filter.sentiment().equalsIgnoreCase(sentimentOf(entity));
  }

  private static boolean matchesAspect(List<AspectSentiment> aspects, ReviewFilter filter) {
    if (filter.aspect() == null) {
      return true;
    }
    for (AspectSentiment aspect : aspects) {
      if (aspect.aspect().equalsIgnoreCase(filter.aspect())) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesMinRating(ReviewFact fact, ReviewFilter filter) {
    return filter.minRating() == null
        || (fact.overallRating() != null && fact.overallRating() >= filter.minRating());
  }

  private static boolean matchesQuery(ReviewFact fact, ReviewFilter filter) {
    if (filter.query() == null || filter.query().isBlank()) {
      return true;
    }
    String query = filter.query().toLowerCase();
    return (fact.comment() != null && fact.comment().toLowerCase().contains(query))
        || (fact.guestName() != null && fact.guestName().toLowerCase().contains(query));
  }

  private Map<String, ReviewClassificationEntity> classificationById() {
    Map<String, ReviewClassificationEntity> map = new HashMap<>();
    for (ReviewClassificationEntity entity : classifications.findAll()) {
      map.put(entity.reviewId(), entity);
    }
    return map;
  }

  /** Parses the {@code aspects jsonb} text back to the classification value records. */
  private static List<AspectSentiment> parseAspects(String aspectsJson) {
    if (aspectsJson == null || aspectsJson.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(aspectsJson, new TypeReference<List<AspectSentiment>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse review aspects", e);
    }
  }

  private static String sentimentOf(ReviewClassificationEntity entity) {
    return entity == null ? null : entity.sentiment();
  }

  /** Increments the {@code [positive, neutral, negative]} counters at {@code offset} 0. */
  private static void incrementSentiment(long[] counters, String sentiment) {
    incrementSentiment(counters, 0, sentiment);
  }

  /** Increments one of {@code [.., positive, neutral, negative]} counters at {@code offset}. */
  private static void incrementSentiment(long[] counters, int offset, String sentiment) {
    if (POSITIVE.equals(sentiment)) {
      counters[offset]++;
    } else if (NEUTRAL.equals(sentiment)) {
      counters[offset + 1]++;
    } else if (NEGATIVE.equals(sentiment)) {
      counters[offset + 2]++;
    }
  }

  private Instant start(LocalDate from) {
    return from.atStartOfDay(zone).toInstant();
  }

  private Instant end(LocalDate to) {
    return to.plusDays(1).atStartOfDay(zone).toInstant();
  }
}
