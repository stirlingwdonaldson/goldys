package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An append-only manual override for one date/service-period's resolved covers. A new override
 * supersedes the prior one for the same key; neither is ever mutated or deleted.
 */
@Entity
@Table(name = "reservation_override")
class ReservationOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "service_period", nullable = false, updatable = false)
  private String servicePeriod;

  @Column(name = "overridden_covers")
  private Long overriddenCovers;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected ReservationOverride() {}

  private ReservationOverride(
      LocalDate tradingDate,
      String servicePeriod,
      Long overriddenCovers,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate, "tradingDate");
    this.servicePeriod = Objects.requireNonNull(servicePeriod, "servicePeriod");
    this.overriddenCovers = overriddenCovers;
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static ReservationOverride create(
      LocalDate tradingDate,
      String servicePeriod,
      Long overriddenCovers,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    return new ReservationOverride(
        tradingDate, servicePeriod, overriddenCovers, reason, actorEmail, recordedAt);
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

  String servicePeriod() {
    return servicePeriod;
  }

  Long overriddenCovers() {
    return overriddenCovers;
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
