package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One date's resolved deleted-sale totals. Disposable projection; reconstructed from {@code
 * canonical_deleted_sale} by {@link DeletedSaleProjector}. Deleted sales are single-source
 * (Lightspeed), so there is no multi-source conflict.
 */
@Entity
@Table(name = "resolved_deleted_sale_day")
class ResolvedDeletedSaleDay {
  @Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(name = "deleted_count", nullable = false)
  private long deletedCount;

  @Column(name = "total_inc_tax", nullable = false)
  private BigDecimal totalIncTax;

  @Column(name = "total_tax", nullable = false)
  private BigDecimal totalTax;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedDeletedSaleDay() {}

  ResolvedDeletedSaleDay(
      LocalDate tradingDate,
      long deletedCount,
      BigDecimal totalIncTax,
      BigDecimal totalTax,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.deletedCount = deletedCount;
    this.totalIncTax = totalIncTax;
    this.totalTax = totalTax;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  long deletedCount() {
    return deletedCount;
  }

  BigDecimal totalIncTax() {
    return totalIncTax;
  }

  BigDecimal totalTax() {
    return totalTax;
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
