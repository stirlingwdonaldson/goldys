package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted review-classification run, used only by the backfill runner (on-ingest classification
 * is accounted for by the existing ingestion-run stage). Minimal, mirroring {@link
 * ClassificationRun}: the run kind, lifecycle status, review count and — once finished — the finish
 * timestamp and any operator-facing error message.
 */
@Entity
@Table(name = "review_classification_run")
class ReviewClassificationRun {

  @Id private UUID id;

  @Column(name = "kind", nullable = false, length = 16)
  private String kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ClassificationRunStatus status;

  @Column(name = "review_count")
  private Integer reviewCount;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_message")
  private String errorMessage;

  protected ReviewClassificationRun() {}

  private ReviewClassificationRun(String kind, Instant now) {
    this.id = UUID.randomUUID();
    this.kind = Objects.requireNonNull(kind, "kind");
    this.status = ClassificationRunStatus.RUNNING;
    this.reviewCount = null;
    this.startedAt = Objects.requireNonNull(now, "now");
    this.finishedAt = null;
    this.errorMessage = null;
  }

  public static ReviewClassificationRun create(String kind, Instant now) {
    return new ReviewClassificationRun(kind, now);
  }

  /** Records a successful run with the number of reviews it covered. */
  public void markSucceeded(int count, Instant now) {
    this.status = ClassificationRunStatus.SUCCEEDED;
    this.reviewCount = count;
    this.finishedAt = Objects.requireNonNull(now, "now");
    this.errorMessage = null;
  }

  /** Records a failed run with an operator-facing message (never credentials or payloads). */
  public void markFailed(String message, Instant now) {
    this.status = ClassificationRunStatus.FAILED;
    this.errorMessage = Objects.requireNonNull(message, "message");
    this.finishedAt = Objects.requireNonNull(now, "now");
  }

  public UUID id() {
    return id;
  }

  public String kind() {
    return kind;
  }

  public ClassificationRunStatus status() {
    return status;
  }

  public Integer reviewCount() {
    return reviewCount;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public String errorMessage() {
    return errorMessage;
  }
}
