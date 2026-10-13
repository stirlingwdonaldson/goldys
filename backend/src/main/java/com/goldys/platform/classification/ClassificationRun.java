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
 * A persisted classification run: the pipeline kind, lifecycle status, product count and — once the
 * run has finished — the finish timestamp and any operator-facing error message. The cluster and
 * projection artifacts are stored on separate tables keyed by {@code run_id}.
 */
@Entity
@Table(name = "classification_run")
class ClassificationRun {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private ClassificationRunKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ClassificationRunStatus status;

  @Column(name = "product_count")
  private Integer productCount;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_message")
  private String errorMessage;

  protected ClassificationRun() {}

  private ClassificationRun(ClassificationRunKind kind, Instant now) {
    this.id = UUID.randomUUID();
    this.kind = Objects.requireNonNull(kind, "kind");
    this.status = ClassificationRunStatus.RUNNING;
    this.productCount = null;
    this.startedAt = Objects.requireNonNull(now, "now");
    this.finishedAt = null;
    this.errorMessage = null;
  }

  public static ClassificationRun create(ClassificationRunKind kind, Instant now) {
    return new ClassificationRun(kind, now);
  }

  /** (Re)marks the run as in-flight; idempotent, mirrors the {@code ForecastRun} lifecycle. */
  public void markRunning() {
    this.status = ClassificationRunStatus.RUNNING;
  }

  /** Records a successful run with the number of products it covered. */
  public void markSucceeded(int productCount, Instant now) {
    this.status = ClassificationRunStatus.SUCCEEDED;
    this.productCount = productCount;
    this.finishedAt = Objects.requireNonNull(now, "now");
    this.errorMessage = null;
  }

  /** Records a failed run with an operator-facing message (never credentials or payloads). */
  public void markFailed(String message, Instant now) {
    this.status = ClassificationRunStatus.FAILED;
    this.errorMessage = Objects.requireNonNull(message, "message");
    this.finishedAt = Objects.requireNonNull(now, "now");
  }

  /** Records a partially completed run (some products processed) with a reason. */
  public void markPartial(String message, Instant now) {
    this.status = ClassificationRunStatus.PARTIAL;
    this.errorMessage = Objects.requireNonNull(message, "message");
    this.finishedAt = Objects.requireNonNull(now, "now");
  }

  public UUID id() {
    return id;
  }

  public ClassificationRunKind kind() {
    return kind;
  }

  public ClassificationRunStatus status() {
    return status;
  }

  public Integer productCount() {
    return productCount;
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
