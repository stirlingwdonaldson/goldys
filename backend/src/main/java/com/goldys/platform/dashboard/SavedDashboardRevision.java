package com.goldys.platform.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One snapshot of a dashboard document, written on every create/update/restore. {@link #document}
 * is the serialized {@code DashboardDocument} JSON; the current document lives in {@code
 * saved_dashboard} and points at its latest revision number.
 */
@Entity
@Table(name = "saved_dashboard_revision")
public class SavedDashboardRevision {
  @Id private UUID id;

  @Column(name = "dashboard_id", nullable = false)
  private UUID dashboardId;

  @Column(name = "revision", nullable = false)
  private int revision;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "document", nullable = false, columnDefinition = "jsonb")
  private String document;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SavedDashboardRevision() {}

  private SavedDashboardRevision(
      UUID id,
      UUID dashboardId,
      int revision,
      String document,
      String createdBy,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.dashboardId = Objects.requireNonNull(dashboardId, "dashboardId");
    this.revision = revision;
    this.document = Objects.requireNonNull(document, "document");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static SavedDashboardRevision create(
      UUID dashboardId, int revision, String document, String createdBy, Instant now) {
    return new SavedDashboardRevision(
        UUID.randomUUID(), dashboardId, revision, document, createdBy, now);
  }

  public UUID id() {
    return id;
  }

  public UUID dashboardId() {
    return dashboardId;
  }

  public int revision() {
    return revision;
  }

  public String document() {
    return document;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
