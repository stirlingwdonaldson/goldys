package com.goldys.platform.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A saved, versioned query definition. The plan is stored as JSON and re-validated on every run.
 */
@Entity
@Table(name = "saved_query")
class SavedQuery {
  @Id private UUID id;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "plan_json", nullable = false)
  private String planJson;

  @Column(name = "current_revision", nullable = false)
  private int currentRevision;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SavedQuery() {}

  private SavedQuery(
      UUID id,
      String createdBy,
      String title,
      String planJson,
      int currentRevision,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.createdBy = createdBy;
    this.title = title;
    this.planJson = planJson;
    this.currentRevision = currentRevision;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  static SavedQuery create(UUID id, String createdBy, String title, String planJson, Instant now) {
    return new SavedQuery(id, createdBy, title, planJson, 1, now, now);
  }

  void update(String title, String planJson, int revision, Instant now) {
    this.title = title;
    this.planJson = planJson;
    this.currentRevision = revision;
    this.updatedAt = now;
  }

  UUID id() {
    return id;
  }

  String createdBy() {
    return createdBy;
  }

  String title() {
    return title;
  }

  String planJson() {
    return planJson;
  }

  int currentRevision() {
    return currentRevision;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }
}
