package com.goldys.platform.classification;

import com.goldys.platform.canonical.CanonicalReviewQuery;
import com.goldys.platform.canonical.ReviewFact;
import com.goldys.platform.canonical.ReviewRecorded;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orchestrates review classification: on each canonicalized review, build the feature text,
 * classify it via the {@link ReviewClassifier}, and upsert the single {@code review_classification}
 * row (latest-wins). A {@code backfill()} pass classifies any history missing a row under a {@code
 * review_classification_run} (kind {@code BACKFILL}).
 *
 * <p>Classification is read-only: a classifier failure on the on-ingest path is swallowed so one
 * bad review never fails the ingestion run; the backfill pass instead surfaces a failure on its own
 * run record so operators can see the backfill did not complete.
 */
@Service
public class ReviewClassificationService {

  /** The aspect taxonomy the classifier may detect sentiment for (Review Focus #1). */
  static final List<String> DEFAULT_ASPECTS =
      List.of("food", "service", "ambience", "value", "noise", "wait_time");

  /** The run kind recorded for a {@link #backfill()} pass. */
  static final String BACKFILL_KIND = "BACKFILL";

  private final ReviewFeatureTextExtractor featureTexts;
  private final ReviewClassifier classifier;
  private final ReviewClassificationRepository reviewClassifications;
  private final ReviewClassificationRunRepository runs;
  private final CanonicalReviewQuery reviews;
  private final Clock clock;

  @Autowired
  public ReviewClassificationService(
      ReviewFeatureTextExtractor featureTexts,
      ReviewClassifier classifier,
      ReviewClassificationRepository reviewClassifications,
      ReviewClassificationRunRepository runs,
      CanonicalReviewQuery reviews) {
    this(featureTexts, classifier, reviewClassifications, runs, reviews, Clock.systemUTC());
  }

  /** Test-friendly constructor with an explicit clock, so tests control classifiedAt/startedAt. */
  ReviewClassificationService(
      ReviewFeatureTextExtractor featureTexts,
      ReviewClassifier classifier,
      ReviewClassificationRepository reviewClassifications,
      ReviewClassificationRunRepository runs,
      CanonicalReviewQuery reviews,
      Clock clock) {
    this.featureTexts = featureTexts;
    this.classifier = classifier;
    this.reviewClassifications = reviewClassifications;
    this.runs = runs;
    this.reviews = reviews;
    this.clock = clock;
  }

  /**
   * Classifies one review and upserts its {@code review_classification} row. A {@link
   * ReviewClassifier} failure is swallowed and the review skipped: classification is read-only and
   * one bad review must not fail the (ingestion) run.
   */
  public void classify(
      String reviewId,
      String source,
      Integer overallRating,
      String comment,
      String privateNote,
      List<String> tags) {
    try {
      classifyUnchecked(reviewId, source, overallRating, comment, privateNote, tags);
    } catch (RuntimeException e) {
      // Swallowed: no row, no propagation.
    }
  }

  /**
   * The classify-on-ingest bridge: classification reads canonical in the allowed leaf direction.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onReviewRecorded(ReviewRecorded event) {
    classify(
        event.reviewId(),
        event.source(),
        event.overallRating(),
        event.comment(),
        event.privateNote(),
        event.tags());
  }

  /**
   * Classifies every current review missing a {@code review_classification} row, under a {@code
   * BACKFILL} run that advances {@code RUNNING} → {@code SUCCEEDED} (with the review count) or
   * {@code FAILED} (with an error message). Synchronous: {@link ReviewClassificationBackfillRunner}
   * submits it to the application task executor so startup is never blocked.
   */
  public void backfill() {
    ReviewClassificationRun run = ReviewClassificationRun.create(BACKFILL_KIND, clock.instant());
    runs.save(run);
    Instant now = clock.instant();
    try {
      int count = 0;
      for (ReviewFact review : reviews.allCurrentReviews()) {
        if (reviewClassifications.existsById(review.reviewId())) {
          continue;
        }
        classifyUnchecked(
            review.reviewId(),
            review.source(),
            review.overallRating(),
            review.comment(),
            review.privateNote(),
            review.tags());
        count++;
      }
      run.markSucceeded(count, now);
    } catch (RuntimeException e) {
      run.markFailed(failureMessage(e), now);
    }
    runs.save(run);
  }

  /** Builds feature text, classifies, and upserts — without swallowing a classifier failure. */
  private void classifyUnchecked(
      String reviewId,
      String source,
      Integer overallRating,
      String comment,
      String privateNote,
      List<String> tags) {
    String text = featureTexts.featureText(source, overallRating, comment, privateNote, tags);
    ReviewClassification classification =
        classifier.classify(new ReviewClassificationRequest(text, DEFAULT_ASPECTS));
    if (classification == null) {
      return;
    }
    reviewClassifications.save(
        ReviewClassificationEntity.create(reviewId, classification, clock.instant()));
  }

  private String failureMessage(RuntimeException e) {
    // JevException messages are already operator-facing (no payload, credentials, or tokens).
    // Anything else gets a class-only label.
    if (e instanceof JevException) {
      return e.getMessage();
    }
    return "unexpected error: " + e.getClass().getSimpleName();
  }
}
