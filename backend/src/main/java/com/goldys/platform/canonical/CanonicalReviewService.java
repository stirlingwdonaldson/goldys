package com.goldys.platform.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records canonical review facts, locking the current source fact so concurrent corrections cannot
 * leave two current versions. The logical identity is a deterministic UUID of the review id, so the
 * same review from two sources shares an id without a matching step.
 */
@Service
class CanonicalReviewService {
  private final CanonicalReviewRepository repository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;

  @Autowired
  CanonicalReviewService(
      CanonicalReviewRepository repository, ApplicationEventPublisher publisher) {
    this(repository, publisher, Clock.systemUTC());
  }

  /** Test-friendly constructor with an explicit clock, so tests control recordedAt. */
  CanonicalReviewService(
      CanonicalReviewRepository repository, ApplicationEventPublisher publisher, Clock clock) {
    this.repository = repository;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Transactional
  CanonicalReview record(ReviewInput input) {
    Instant now = clock.instant();
    UUID logicalId = logicalIdFor(input.reviewId());
    String ref = input.reviewId();
    Optional<CanonicalReview> current = repository.lockCurrentReview(ref, input.sourceSystem());

    if (current.isPresent()) {
      CanonicalReview existing = current.get();
      if (existing.sameFact(input)) {
        return existing;
      }
      existing.supersede(now);
      repository.saveAndFlush(existing);
    }

    CanonicalReview saved =
        repository.save(
            CanonicalReview.create(
                logicalId, input.sourceSystem(), ref, input.rawRecordId(), now, now, input));
    publisher.publishEvent(
        new ReviewRecorded(
            input.reviewId(),
            input.source(),
            input.overallRating(),
            input.comment(),
            input.privateNote(),
            input.tags()));
    return saved;
  }

  private static UUID logicalIdFor(String reviewId) {
    return UUID.nameUUIDFromBytes(("review:" + reviewId).getBytes(StandardCharsets.UTF_8));
  }
}
