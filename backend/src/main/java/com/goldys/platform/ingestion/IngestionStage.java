package com.goldys.platform.ingestion;

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
 * One observed outcome of one pipeline stage for one dataset within an ingestion run, recorded
 * append-only. A push that stored raw bytes but then failed to parse is still {@code SUCCESS} on
 * the run (the bytes are safe); this fact keeps the transformation outcome visible instead.
 */
@Entity
@Table(name = "ingestion_stage")
class IngestionStage {
  @Id private UUID id;

  @Column(name = "ingestion_run_id", nullable = false, updatable = false)
  private UUID ingestionRunId;

  @Column(name = "source_system", nullable = false, updatable = false)
  private String sourceSystem;

  @Column(name = "dataset", nullable = false, updatable = false)
  private String dataset;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false, updatable = false, length = 32)
  private IngestionStageKind stage;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 32)
  private IngestionStageOutcome outcome;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected IngestionStage() {}

  private IngestionStage(
      UUID ingestionRunId,
      String sourceSystem,
      String dataset,
      IngestionStageKind stage,
      IngestionStageOutcome outcome,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.ingestionRunId = Objects.requireNonNull(ingestionRunId, "ingestionRunId");
    this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
    this.dataset = Objects.requireNonNull(dataset, "dataset");
    this.stage = Objects.requireNonNull(stage, "stage");
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  static IngestionStage create(
      UUID ingestionRunId,
      String sourceSystem,
      String dataset,
      IngestionStageKind stage,
      IngestionStageOutcome outcome,
      Instant occurredAt) {
    return new IngestionStage(ingestionRunId, sourceSystem, dataset, stage, outcome, occurredAt);
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

  String dataset() {
    return dataset;
  }

  IngestionStageKind stage() {
    return stage;
  }

  IngestionStageOutcome outcome() {
    return outcome;
  }

  Instant occurredAt() {
    return occurredAt;
  }
}
