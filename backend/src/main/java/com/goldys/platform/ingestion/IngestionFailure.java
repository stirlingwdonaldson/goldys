package com.goldys.platform.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A first-class connector failure, stored separately from payload rows so a failed fetch can never
 * be mistaken for "no new data".
 *
 * <p>{@code failureType} stays a free string on purpose: a new adapter can report an unanticipated
 * failure mode without a migration. Detail text is operator-facing and must never carry payload
 * contents, credentials, or tokens.
 */
@Entity
@Table(name = "ingestion_failure")
class IngestionFailure {
  @Id private UUID id;

  @Column(name = "ingestion_run_id", nullable = false, updatable = false)
  private UUID ingestionRunId;

  @Column(name = "source_system", nullable = false, updatable = false)
  private String sourceSystem;

  @Column(name = "failure_type", nullable = false, updatable = false)
  private String failureType;

  @Column(name = "detail", updatable = false)
  private String detail;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected IngestionFailure() {}

  private IngestionFailure(
      UUID ingestionRunId,
      String sourceSystem,
      String failureType,
      String detail,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.ingestionRunId = Objects.requireNonNull(ingestionRunId, "ingestionRunId");
    this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
    this.failureType = requireFailureType(failureType);
    this.detail = detail;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  static IngestionFailure record(
      UUID ingestionRunId,
      String sourceSystem,
      String failureType,
      String detail,
      Instant occurredAt) {
    return new IngestionFailure(ingestionRunId, sourceSystem, failureType, detail, occurredAt);
  }

  private static String requireFailureType(String failureType) {
    Objects.requireNonNull(failureType, "failureType");
    if (failureType.isBlank()) {
      throw new IllegalArgumentException("failureType must not be blank");
    }
    return failureType;
  }

  UUID id() {
    return id;
  }

  UUID ingestionRunId() {
    return ingestionRunId;
  }

  String sourceSystem() {
    return sourceSystem;
  }

  String failureType() {
    return failureType;
  }

  String detail() {
    return detail;
  }

  Instant occurredAt() {
    return occurredAt;
  }
}
