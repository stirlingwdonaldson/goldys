package com.goldys.platform.reconciliation;

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
 * An append-only standing resolution rule for one reconciliation unit ({@code entity_type} + {@code
 * field_key}). A new row supersedes the prior one for the same key; rows are never mutated.
 */
@Entity
@Table(name = "resolution_rule")
class ResolutionRule {
  @Id private UUID id;

  @Column(name = "entity_type", nullable = false, updatable = false)
  private String entityType;

  @Column(name = "field_key", nullable = false, updatable = false)
  private String fieldKey;

  @Column(name = "strategy", nullable = false, updatable = false)
  private String strategy;

  @Column(name = "custom_logic", updatable = false)
  private String customLogic;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_priority", updatable = false, columnDefinition = "jsonb")
  private List<String> sourcePriority;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  @Column(name = "superseded_by")
  private String supersededBy;

  protected ResolutionRule() {}

  private ResolutionRule(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.fieldKey = Objects.requireNonNull(fieldKey, "fieldKey");
    this.strategy = Objects.requireNonNull(strategy, "strategy");
    this.customLogic = customLogic;
    this.sourcePriority = sourcePriority;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static ResolutionRule create(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority,
      String actorEmail,
      Instant recordedAt) {
    return new ResolutionRule(
        entityType, fieldKey, strategy, customLogic, sourcePriority, actorEmail, recordedAt);
  }

  void supersede(Instant at, String supersededBy) {
    if (supersededAt != null) {
      throw new IllegalStateException("Rule " + id + " is already superseded");
    }
    this.supersededAt = Objects.requireNonNull(at, "at");
    this.supersededBy = supersededBy;
  }

  UUID id() {
    return id;
  }

  String entityType() {
    return entityType;
  }

  String fieldKey() {
    return fieldKey;
  }

  String strategy() {
    return strategy;
  }

  String customLogic() {
    return customLogic;
  }

  List<String> sourcePriority() {
    return sourcePriority;
  }

  String actorEmail() {
    return actorEmail;
  }

  Instant recordedAt() {
    return recordedAt;
  }

  Instant supersededAt() {
    return supersededAt;
  }

  String supersededBy() {
    return supersededBy;
  }
}
