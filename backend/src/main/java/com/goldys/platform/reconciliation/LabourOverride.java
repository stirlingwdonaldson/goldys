package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An append-only manual override for one date/department's resolved actual hours. A new override
 * supersedes the prior one for the same key; neither is ever mutated or deleted.
 */
@Entity
@Table(name = "labour_override")
class LabourOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "department", nullable = false, updatable = false)
  private String department;

  @Column(name = "overridden_actual_hours")
  private BigDecimal overriddenActualHours;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected LabourOverride() {}

  private LabourOverride(
      LocalDate tradingDate,
      String department,
      BigDecimal overriddenActualHours,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate, "tradingDate");
    this.department = Objects.requireNonNull(department, "department");
    this.overriddenActualHours = overriddenActualHours;
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static LabourOverride create(
      LocalDate tradingDate,
      String department,
      BigDecimal overriddenActualHours,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    return new LabourOverride(
        tradingDate, department, overriddenActualHours, reason, actorEmail, recordedAt);
  }

  void supersede(Instant at) {
    if (supersededAt != null) {
      throw new IllegalStateException("Override " + id + " is already superseded");
    }
    this.supersededAt = Objects.requireNonNull(at, "at");
  }

  UUID id() {
    return id;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String department() {
    return department;
  }

  BigDecimal overriddenActualHours() {
    return overriddenActualHours;
  }

  String reason() {
    return reason;
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
}
