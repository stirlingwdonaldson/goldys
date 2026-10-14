package com.goldys.platform.classification;

import com.goldys.platform.canonical.CanonicalReviewQuery;
import com.goldys.platform.canonical.ReviewFact;
import com.goldys.platform.canonical.ReviewRecorded;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates review classification: on each canonicalized review, build the feature text,
 * classify it via the {@link ReviewClassifier}, and upsert the single {@code review_classification}
 * row (latest-wins). A {@code backfill()} pass classifies any history missing a row under a {@code
 * review_classification_run} (kind {@code BACKFILL}).
 *
 * <p>Classification is gated on {@code classification.jev.enabled}: when disabled, no classifier is
 * called and no row is persisted (privacy — the hosted service is never contacted). Classification
 * is read-only: a classifier failure on the on-ingest path is swallowed so one bad review never
 * fails the ingestion run; the backfill pass instead surfaces a failure on its own run record so
 * operators can see the backfill did not complete.
 */
@Service
public class ReviewClassificationService {

  /** The aspect taxonomy the classifier may detect sentiment for (Review Focus #1). */
  static final List<String> DEFAULT_ASPECTS =
      List.of("food", "service", "ambience", "value", "noise", "wait_time");

  /** The run kind recorded for a {@link #backfill()} pass. */
  static final String BACKFILL_KIND = "BACKFILL";

  private static final Logger log = LoggerFactory.getLogger(ReviewClassificationService.class);

  private final ReviewFeatureTextExtractor featureTexts;
  private final ReviewClassifier classifier;
  private final ReviewClassificationRepository reviewClassifications;
  private final ReviewClassificationRunRepository runs;
  private final CanonicalReviewQuery reviews;
  private final JevProperties jevProperties;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  @Autowired
  public ReviewClassificationService(
      ReviewFeatureTextExtractor featureTexts,
      ReviewClassifier classifier,
      ReviewClassificationRepository reviewClassifications,
      ReviewClassificationRunRepository runs,
      CanonicalReviewQuery reviews,
      JevProperties jevProperties,
      PlatformTransactionManager transactionManager) {
    this(
        featureTexts,
        classifier,
        reviewClassifications,
        runs,
        reviews,
        jevProperties,
        transactionManager,
        Clock.systemUTC());
  }

  /** Test-friendly constructor with an explicit clock, so tests control classifiedAt/startedAt. */
  ReviewClassificationService(
      ReviewFeatureTextExtractor featureTexts,
      ReviewClassifier classifier,
      ReviewClassificationRepository reviewClassifications,
      ReviewClassificationRunRepository runs,
      CanonicalReviewQuery reviews,
      JevProperties jevProperties,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.featureTexts = featureTexts;
    this.classifier = classifier;
    this.reviewClassifications = reviewClassifications;
    this.runs = runs;
    this.reviews = reviews;
    this.jevProperties = jevProperties;
    this.transactionTemplate = newTransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /**
   * Classifies one review and upserts its {@code review_classification} row. Classification is
   * skipped — with no classifier call and no persistence — when Jev is disabled or the review has
   * no text (comment, private note and tags all blank). Otherwise only a {@link ReviewClassifier}
   * failure is swallowed (and logged) — one bad review or a Jev hiccup must not fail the ingestion
   * run — while a feature-text or persistence failure propagates so it is not silently lost.
   */
  public void classify(
      String reviewId,
      String source,
      Integer overallRating,
      String comment,
      String privateNote,
      List<String> tags) {
    if (!jevProperties.enabled()) {
      log.debug(
          "Skipping classification for review '{}': Jev classification is disabled", reviewId);
      return;
    }
    if (isTextless(comment, privateNote, tags)) {
      log.debug("Skipping classification for review '{}': no review text", reviewId);
      return;
    }
    String text = featureTexts.featureText(source, overallRating, comment, privateNote, tags);
    ReviewClassification classification;
    try {
      classification = classifier.classify(new ReviewClassificationRequest(text, DEFAULT_ASPECTS));
    } catch (RuntimeException e) {
      log.warn("Skipping classification for review '{}': classifier failed", reviewId, e);
      return;
    }
    persist(reviewId, classification);
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
      requireEnabled();
      int count = 0;
      for (ReviewFact review : reviews.allCurrentReviews()) {
        if (reviewClassifications.existsById(review.reviewId())) {
          continue;
        }
        if (isTextless(review.comment(), review.privateNote(), review.tags())) {
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

  /**
   * Rejects a backfill run when Jev is disabled, mirroring the product classifier's {@code
   * requireEnabled}: a run against an un-deployed or un-warmed sidecar fails fast with an
   * operator-facing message recorded in {@code error_message} rather than timing out deep in the
   * HTTP client.
   */
  private void requireEnabled() {
    if (!jevProperties.enabled()) {
      throw new JevException(
          "Review Jev classification is disabled; set CLASSIFICATION_JEV_ENABLED=true and"
              + " CLASSIFICATION_JEV_API_KEY");
    }
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
    persist(reviewId, classification);
  }

  /**
   * Upserts the single {@code review_classification} row, skipping a null (unclassified) result.
   *
   * <p>Persisted in an independently committed transaction ({@code REQUIRES_NEW}): the {@code
   * AFTER_COMMIT} listener still runs with the writer's transaction synchronization bound, so a
   * plain repository {@code save()} would join the already-committed transaction and be lost.
   */
  private void persist(String reviewId, ReviewClassification classification) {
    if (classification == null) {
      return;
    }
    transactionTemplate.executeWithoutResult(
        status ->
            reviewClassifications.save(
                ReviewClassificationEntity.create(reviewId, classification, clock.instant())));
  }

  /**
   * True when a review has no classifier-readable text: comment, private note and tags all blank.
   */
  private static boolean isTextless(String comment, String privateNote, List<String> tags) {
    if (notBlank(comment) || notBlank(privateNote)) {
      return false;
    }
    if (tags != null) {
      for (String tag : tags) {
        if (notBlank(tag)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static TransactionTemplate newTransactionTemplate(PlatformTransactionManager txManager) {
    TransactionTemplate template = new TransactionTemplate(txManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
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
