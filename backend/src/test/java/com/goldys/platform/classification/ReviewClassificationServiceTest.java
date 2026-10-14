package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReviewQuery;
import com.goldys.platform.canonical.ReviewFact;
import com.goldys.platform.canonical.ReviewRecorded;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Orchestration coverage: classify-on-ingest, latest-wins upsert, and the backfill runner. */
class ReviewClassificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

  private final CanonicalReviewQuery reviews = mock(CanonicalReviewQuery.class);
  private final ReviewClassificationRepository reviewClassifications =
      mock(ReviewClassificationRepository.class);
  private final ReviewClassificationRunRepository runs =
      mock(ReviewClassificationRunRepository.class);

  private final Map<String, ReviewClassificationEntity> classificationStore = new HashMap<>();
  private final List<ReviewClassificationRun> runStore = new ArrayList<>();

  @BeforeEach
  void setUp() {
    classificationStore.clear();
    runStore.clear();

    when(reviewClassifications.save(any()))
        .thenAnswer(
            inv -> {
              ReviewClassificationEntity entity = inv.getArgument(0);
              classificationStore.put(entity.reviewId(), entity);
              return entity;
            });
    when(reviewClassifications.existsById(any()))
        .thenAnswer(inv -> classificationStore.containsKey(inv.getArgument(0)));

    when(runs.save(any()))
        .thenAnswer(
            inv -> {
              ReviewClassificationRun run = inv.getArgument(0);
              runStore.add(run);
              return run;
            });
  }

  private ReviewClassificationService service(ReviewClassifier classifier) {
    return new ReviewClassificationService(
        new ReviewFeatureTextExtractor(),
        classifier,
        reviewClassifications,
        runs,
        reviews,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void classify_upserts_latest_wins() {
    FakeClassifier classifier =
        new FakeClassifier(
            req ->
                new ReviewClassification(
                    new Sentiment("POSITIVE", new BigDecimal("0.90")),
                    new BigDecimal("4.5"),
                    List.of()));
    ReviewClassificationService svc = service(classifier);

    svc.classify("OT-1", "OpenTable", 4, "Great food", null, List.of());

    classifier.setHandler(
        req ->
            new ReviewClassification(
                new Sentiment("NEGATIVE", new BigDecimal("0.80")),
                new BigDecimal("2.0"),
                List.of()));
    svc.classify("OT-1", "OpenTable", 4, "Great food", null, List.of());

    assertThat(classificationStore).hasSize(1);
    ReviewClassificationEntity saved = classificationStore.get("OT-1");
    assertThat(saved.sentiment()).isEqualTo("NEGATIVE");
    assertThat(saved.sentimentConfidence()).isEqualByComparingTo("0.80");
    assertThat(saved.predictedRating()).isEqualByComparingTo("2.0");
  }

  @Test
  void feature_text_falls_back_to_source_and_rating() {
    ReviewFeatureTextExtractor extractor = new ReviewFeatureTextExtractor();

    String text = extractor.featureText("OpenTable", 3, null, null, List.of());

    assertThat(text).isNotBlank();
    assertThat(text).contains("OpenTable");
    assertThat(text).contains("3");
  }

  @Test
  void classifier_throw_is_skipped_not_fatal() {
    ReviewClassifier throwing =
        req -> {
          throw new JevException("Jev service returned HTTP 500");
        };
    ReviewClassificationService svc = service(throwing);

    assertThatCode(() -> svc.classify("OT-1", "OpenTable", 4, "Great food", null, List.of()))
        .doesNotThrowAnyException();
    assertThat(classificationStore).isEmpty();
  }

  @Test
  void persistence_failure_is_not_swallowed() {
    doThrow(new RuntimeException("db down")).when(reviewClassifications).save(any());
    ReviewClassificationService svc =
        service(
            new FakeClassifier(
                req ->
                    new ReviewClassification(
                        new Sentiment("POSITIVE", new BigDecimal("0.90")),
                        new BigDecimal("4.5"),
                        List.of())));

    assertThatThrownBy(() -> svc.classify("OT-1", "OpenTable", 4, "Great food", null, List.of()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");
  }

  @Test
  void on_review_recorded_classifies() {
    FakeClassifier classifier =
        new FakeClassifier(
            req ->
                new ReviewClassification(
                    new Sentiment("POSITIVE", new BigDecimal("0.90")),
                    new BigDecimal("4.5"),
                    List.of()));
    ReviewClassificationService svc = service(classifier);

    svc.onReviewRecorded(new ReviewRecorded("OT-1", "OpenTable", 4, "Great food", null, List.of()));

    assertThat(classificationStore).containsKey("OT-1");
    assertThat(classifier.requests()).hasSize(1);
    assertThat(classifier.requests().get(0).featureText()).contains("Great food");
  }

  @Test
  void backfill_marks_run_succeeded() {
    when(reviews.allCurrentReviews())
        .thenReturn(
            List.of(
                reviewFact("OT-1", "OpenTable", 4, "Great food"),
                reviewFact("OT-2", "Google", 5, "Amazing night")));
    FakeClassifier classifier =
        new FakeClassifier(
            req ->
                new ReviewClassification(
                    new Sentiment("POSITIVE", new BigDecimal("0.90")),
                    new BigDecimal("4.5"),
                    List.of()));
    ReviewClassificationService svc = service(classifier);

    svc.backfill();

    ReviewClassificationRun run = runStore.get(runStore.size() - 1);
    assertThat(run.status()).isEqualTo(ClassificationRunStatus.SUCCEEDED);
    assertThat(run.reviewCount()).isEqualTo(2);
    assertThat(classificationStore).containsKeys("OT-1", "OT-2");
  }

  @Test
  void backfill_marks_run_failed_when_classifier_throws() {
    when(reviews.allCurrentReviews())
        .thenReturn(List.of(reviewFact("OT-1", "OpenTable", 4, "Great food")));
    ReviewClassifier throwing =
        req -> {
          throw new JevException("Jev service returned HTTP 500");
        };
    ReviewClassificationService svc = service(throwing);

    svc.backfill();

    ReviewClassificationRun run = runStore.get(runStore.size() - 1);
    assertThat(run.status()).isEqualTo(ClassificationRunStatus.FAILED);
    assertThat(run.errorMessage()).isEqualTo("Jev service returned HTTP 500");
    assertThat(classificationStore).isEmpty();
  }

  private static ReviewFact reviewFact(String id, String source, int rating, String comment) {
    return new ReviewFact(
        id, source, NOW, NOW, rating, null, null, null, null, null, null, comment, null, List.of(),
        List.of(), null);
  }

  /** A fake classifier that records each request and delegates to a swappable handler. */
  private static final class FakeClassifier implements ReviewClassifier {
    private final List<ReviewClassificationRequest> requests = new ArrayList<>();
    private Function<ReviewClassificationRequest, ReviewClassification> handler;

    FakeClassifier(Function<ReviewClassificationRequest, ReviewClassification> handler) {
      this.handler = handler;
    }

    void setHandler(Function<ReviewClassificationRequest, ReviewClassification> handler) {
      this.handler = handler;
    }

    @Override
    public ReviewClassification classify(ReviewClassificationRequest request) {
      requests.add(request);
      return handler.apply(request);
    }

    List<ReviewClassificationRequest> requests() {
      return requests;
    }
  }
}
