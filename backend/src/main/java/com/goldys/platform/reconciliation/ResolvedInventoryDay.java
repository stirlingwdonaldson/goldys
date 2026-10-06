package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One date's resolved inventory value. Disposable projection; {@code wastage}/{@code stock_on_hand}
 * are null while there is no source data.
 */
@Entity
@Table(name = "resolved_inventory_day")
class ResolvedInventoryDay {
  @Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "purchases", precision = 14, scale = 4)
  private BigDecimal purchases;

  @Column(name = "wastage", precision = 14, scale = 4)
  private BigDecimal wastage;

  @Column(name = "stock_on_hand", precision = 14, scale = 4)
  private BigDecimal stockOnHand;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedInventoryDay() {}

  ResolvedInventoryDay(
      LocalDate tradingDate,
      BigDecimal purchases,
      BigDecimal wastage,
      BigDecimal stockOnHand,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.purchases = purchases;
    this.wastage = wastage;
    this.stockOnHand = stockOnHand;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  BigDecimal purchases() {
    return purchases;
  }

  BigDecimal wastage() {
    return wastage;
  }

  BigDecimal stockOnHand() {
    return stockOnHand;
  }

  String resolutionType() {
    return resolutionType;
  }

  String authoritativeSource() {
    return authoritativeSource;
  }

  boolean hasConflict() {
    return hasConflict;
  }

  Instant resolvedAt() {
    return resolvedAt;
  }
}
