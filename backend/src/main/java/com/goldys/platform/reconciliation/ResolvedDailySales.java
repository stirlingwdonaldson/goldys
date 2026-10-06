package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One trading date's resolved daily-sales value. Disposable projection; {@code totalSales} is null
 * while the date is unresolved.
 */
@Entity
@Table(name = "resolved_daily_sales")
class ResolvedDailySales {
  @Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "total_sales", precision = 14, scale = 4)
  private BigDecimal totalSales;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedDailySales() {}

  ResolvedDailySales(
      LocalDate tradingDate,
      BigDecimal totalSales,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.totalSales = totalSales;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  BigDecimal totalSales() {
    return totalSales;
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
