package com.goldys.platform.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A persisted dashboard document: title, layout, and the ordered widget queries to render. Holds
 * query configuration only — no embedded snapshot data, so rendering always reads current resolved
 * data.
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
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.title = Objects.requireNonNull(title, "title");
    this.description = description;
    this.layout = Objects.requireNonNull(layout, "layout");
    this.widgets = widgets == null ? List.of() : List.copyOf(widgets);
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static SavedDashboard create(
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      String createdBy,
      Instant now) {
    return new SavedDashboard(
        UUID.randomUUID(), title, description, layout, widgets, createdBy, now, now);
  }

  public void update(
      String title, String description, String layout, List<SavedWidget> widgets, Instant now) {
    this.title = Objects.requireNonNull(title, "title");
    this.description = description;
    this.layout = Objects.requireNonNull(layout, "layout");
    this.widgets = widgets == null ? List.of() : List.copyOf(widgets);
    this.updatedAt = now;
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
