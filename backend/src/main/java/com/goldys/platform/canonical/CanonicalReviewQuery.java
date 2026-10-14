package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only query facade for canonical reviews, so modules outside this package never touch the
 * package-private entity or repository directly.
 */
@Service
public class CanonicalReviewQuery {
  private final CanonicalReviewRepository repository;

  public CanonicalReviewQuery(CanonicalReviewRepository repository) {
    this.repository = repository;
  }

  /** Current reviews whose {@code reviewedAt} falls in {@code [from, to)}, mapped to views. */
  public List<ReviewFact> currentReviews(Instant from, Instant to) {
    return repository.findCurrentByReviewedAtBetween(from, to).stream().map(this::toFact).toList();
  }

  /** All current reviews, mapped to views. */
  public List<ReviewFact> allCurrentReviews() {
    return repository.findAllCurrent().stream().map(this::toFact).toList();
  }

  private ReviewFact toFact(CanonicalReview r) {
    return new ReviewFact(
        r.sourceRecordRef(),
        r.source(),
        r.reviewedAt(),
        r.visitedAt(),
        r.overallRating(),
        r.foodRating(),
        r.serviceRating(),
        r.ambienceRating(),
        r.valueRating(),
        r.noiseLabel(),
        r.wouldRecommend(),
        r.comment(),
        r.privateNote(),
        r.tags(),
        r.keywords(),
        r.guestName());
  }
}
