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
 * An append-only manual override: which source is authoritative for a trading date.
 *
 * <p>A new override supersedes the prior one for the same date; neither is ever mutated or deleted.
 */
@Entity
@Table(name = "daily_sales_override")
class DailySalesOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "authoritative_source", nullable = false, updatable = false)
  private String authoritativeSource;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected DailySalesOverride() {}

  private DailySalesOverride(
      LocalDate tradingDate,
      String authoritativeSource,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate, "tradingDate");
    this.authoritativeSource = Objects.requireNonNull(authoritativeSource, "authoritativeSource");
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static DailySalesOverride create(
      LocalDate tradingDate,
      String authoritativeSource,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    return new DailySalesOverride(tradingDate, authoritativeSource, reason, actorEmail, recordedAt);
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

  String authoritativeSource() {
    return authoritativeSource;
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
