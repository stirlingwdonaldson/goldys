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
 * One connector execution, from the moment it opens until it reaches a terminal state.
 *
 * <p>The run is the unit an operator asks "did this source work?" about, so its terminal state is
 * recorded explicitly rather than inferred from how many rows happen to exist.
 */
@Entity
@Table(name = "ingestion_run")
class IngestionRun {
  @Id private UUID id;

  @Column(name = "source_system", nullable = false, updatable = false)
  private String sourceSystem;

  @Column(name = "connector_name", nullable = false, updatable = false)
  private String connectorName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private IngestionStatus status;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "input_watermark", updatable = false)
  private String inputWatermark;

  @Column(name = "output_watermark")
  private String outputWatermark;

  @Column(name = "fetched_count", nullable = false)
  private long fetchedCount;

  @Column(name = "persisted_count", nullable = false)
  private long persistedCount;

  @Column(name = "failure_summary")
  private String failureSummary;

  protected IngestionRun() {}

  private IngestionRun(
      String sourceSystem, String connectorName, String inputWatermark, Instant startedAt) {
    this.id = UUID.randomUUID();
    this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
    this.connectorName = Objects.requireNonNull(connectorName, "connectorName");
    this.inputWatermark = inputWatermark;
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    this.status = IngestionStatus.RUNNING;
  }

  static IngestionRun start(
      String sourceSystem, String connectorName, String inputWatermark, Instant startedAt) {
    return new IngestionRun(sourceSystem, connectorName, inputWatermark, startedAt);
  }

  void recordFetched() {
    requireRunning();
    fetchedCount++;
  }

  void recordPersisted() {
    requireRunning();
    persistedCount++;
  }

  void complete(
      IngestionStatus finalStatus,
      String outputWatermark,
      String failureSummary,
      Instant completedAt) {
    requireRunning();
    if (finalStatus == IngestionStatus.RUNNING) {
      throw new IllegalArgumentException("Final status cannot be RUNNING");
    }
    this.status = Objects.requireNonNull(finalStatus, "finalStatus");
    this.outputWatermark = outputWatermark;
    this.failureSummary = failureSummary;
    this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
  }

  private void requireRunning() {
    if (status != IngestionStatus.RUNNING) {
      throw new IllegalStateException("Ingestion run " + id + " is already " + status);
    }
  }

  UUID id() {
    return id;
  }

  String sourceSystem() {
    return sourceSystem;
  }

  String connectorName() {
    return connectorName;
  }

  IngestionStatus status() {
    return status;
  }

  Instant startedAt() {
    return startedAt;
  }

  Instant completedAt() {
    return completedAt;
  }

  String inputWatermark() {
    return inputWatermark;
  }

  String outputWatermark() {
    return outputWatermark;
  }

  long fetchedCount() {
    return fetchedCount;
  }

  long persistedCount() {
    return persistedCount;
  }

  String failureSummary() {
    return failureSummary;
  }
}
