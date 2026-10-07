package com.goldys.platform.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A persisted dashboard document: title, layout, the ordered widget queries to render, reusable
 * filters, visibility and pin state. Holds query configuration only — no embedded snapshot data, so
 * rendering always reads current resolved data.
 */
@Entity
@Table(name = "saved_dashboard")
public class SavedDashboard {
  @Id private UUID id;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "layout", nullable = false, length = 32)
  private String layout;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "widgets", nullable = false, columnDefinition = "jsonb")
  private List<SavedWidget> widgets;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filters", nullable = false, columnDefinition = "jsonb")
  private DashboardFilters filters;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility", nullable = false, length = 16)
  private Visibility visibility;

  @Column(name = "pinned", nullable = false)
  private boolean pinned;

  @Column(name = "current_revision", nullable = false)
  private int currentRevision;

  @Column(name = "created_by", nullable = false, length = 255)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SavedDashboard() {}

  private SavedDashboard(
      UUID id,
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      DashboardFilters filters,
      Visibility visibility,
      boolean pinned,
      int currentRevision,
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.title = Objects.requireNonNull(title, "title");
    this.description = description;
    this.layout = Objects.requireNonNull(layout, "layout");
    this.widgets = widgets == null ? List.of() : List.copyOf(widgets);
    this.filters = Objects.requireNonNull(filters, "filters");
    this.visibility = Objects.requireNonNull(visibility, "visibility");
    this.pinned = pinned;
    this.currentRevision = currentRevision;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static SavedDashboard create(
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      DashboardFilters filters,
      Visibility visibility,
      String createdBy,
      Instant now) {
    return new SavedDashboard(
        UUID.randomUUID(),
        title,
        description,
        layout,
        widgets,
        filters,
        visibility,
        false,
        1,
        createdBy,
        now,
        now);
  }

  public void update(
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      DashboardFilters filters,
      Visibility visibility,
      Instant now) {
    this.title = Objects.requireNonNull(title, "title");
    this.description = description;
    this.layout = Objects.requireNonNull(layout, "layout");
    this.widgets = widgets == null ? List.of() : List.copyOf(widgets);
    this.filters = Objects.requireNonNull(filters, "filters");
    this.visibility = Objects.requireNonNull(visibility, "visibility");
    this.updatedAt = now;
  }

  /** Advances this document to the next revision number, written as a snapshot on save. */
  public void incrementRevision() {
    this.currentRevision = currentRevision + 1;
  }

  /** Sets the sharing visibility (creator-only, role list, or org-wide). */
  public void setVisibility(Visibility visibility) {
    this.visibility = Objects.requireNonNull(visibility, "visibility");
  }

  public UUID id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public String layout() {
    return layout;
  }

  public List<SavedWidget> widgets() {
    return widgets;
  }

  public DashboardFilters filters() {
    return filters;
  }

  public Visibility visibility() {
    return visibility;
  }

  public boolean pinned() {
    return pinned;
  }

  public int currentRevision() {
    return currentRevision;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
