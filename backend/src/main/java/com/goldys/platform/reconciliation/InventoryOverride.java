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

/** An append-only manual override for one date's resolved purchases (COGS). */
@Entity
@Table(name = "inventory_override")
class InventoryOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "overridden_purchases")
  private BigDecimal overriddenPurchases;

  @Column(name = "reason")
  private String reason;

  @Column(name = "actor_email", nullable = false, updatable = false)
  private String actorEmail;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected InventoryOverride() {}

  private InventoryOverride(
      LocalDate tradingDate,
      BigDecimal overriddenPurchases,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate, "tradingDate");
    this.overriddenPurchases = overriddenPurchases;
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail, "actorEmail");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
  }

  static InventoryOverride create(
      LocalDate tradingDate,
      BigDecimal overriddenPurchases,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    return new InventoryOverride(tradingDate, overriddenPurchases, reason, actorEmail, recordedAt);
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

  BigDecimal overriddenPurchases() {
    return overriddenPurchases;
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
