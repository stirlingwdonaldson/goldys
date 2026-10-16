package com.goldys.platform.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One revision snapshot of a saved query's plan. */
@Entity
@Table(name = "saved_query_revision")
class SavedQueryRevision {
  @Id private UUID id;

  @Column(name = "query_id", nullable = false)
  private UUID queryId;

  @Column(name = "revision", nullable = false)
  private int revision;

  @Column(name = "plan_json", nullable = false)
  private String planJson;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SavedQueryRevision() {}

  private SavedQueryRevision(
      UUID id, UUID queryId, int revision, String planJson, String createdBy, Instant createdAt) {
    this.id = id;
    this.queryId = queryId;
    this.revision = revision;
    this.planJson = planJson;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  static SavedQueryRevision create(
      UUID queryId, int revision, String planJson, String createdBy, Instant createdAt) {
    return new SavedQueryRevision(
        UUID.randomUUID(), queryId, revision, planJson, createdBy, createdAt);
  }

  int revision() {
    return revision;
  }

  String planJson() {
    return planJson;
  }

  String createdBy() {
    return createdBy;
  }

  Instant createdAt() {
    return createdAt;
  }
}
