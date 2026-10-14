package com.goldys.platform.canonical;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CanonicalReviewRepository extends BitemporalRepository<CanonicalReview> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from CanonicalReview r where r.sourceRecordRef = :ref "
          + "and r.sourceSystem = :source and r.supersededAt is null")
  Optional<CanonicalReview> lockCurrentReview(String ref, String source);

  @Query("select r from CanonicalReview r where r.supersededAt is null")
  List<CanonicalReview> findAllCurrent();

  /** Current reviews whose {@code reviewed_at} falls in {@code [from, to)}. */
  @Query(
      "select r from CanonicalReview r where r.supersededAt is null "
          + "and r.reviewedAt >= :from and r.reviewedAt < :to")
  List<CanonicalReview> findCurrentByReviewedAtBetween(Instant from, Instant to);
}
