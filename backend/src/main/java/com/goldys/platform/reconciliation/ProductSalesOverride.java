package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An append-only manual override: which source is authoritative for a product/day. */
@Entity
@Table(name = "product_sales_override")
class ProductSalesOverride {
  @Id private UUID id;

  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

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

  protected ProductSalesOverride() {}

  private ProductSalesOverride(
      LocalDate tradingDate,
      String productNameKey,
      String authoritativeSource,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    this.id = UUID.randomUUID();
    this.tradingDate = Objects.requireNonNull(tradingDate);
    this.productNameKey = Objects.requireNonNull(productNameKey);
    this.authoritativeSource = Objects.requireNonNull(authoritativeSource);
    this.reason = reason;
    this.actorEmail = Objects.requireNonNull(actorEmail);
    this.recordedAt = Objects.requireNonNull(recordedAt);
  }

  static ProductSalesOverride create(
      LocalDate tradingDate,
      String productNameKey,
      String authoritativeSource,
      String reason,
      String actorEmail,
      Instant recordedAt) {
    return new ProductSalesOverride(
        tradingDate, productNameKey, authoritativeSource, reason, actorEmail, recordedAt);
  }

  void supersede(Instant at) {
    if (supersededAt != null) {
      throw new IllegalStateException("Override " + id + " is already superseded");
    }
    this.supersededAt = Objects.requireNonNull(at);
  }

  UUID id() {
    return id;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String productNameKey() {
    return productNameKey;
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
