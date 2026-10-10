package com.goldys.platform.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One date/category's resolved sale-item totals. Disposable projection; reconstructed from {@code
 * canonical_sale_item} by {@link SaleItemProjector}. Sale items are single-source (Lightspeed), so
 * there is no multi-source conflict.
 */
@Entity
@Table(name = "resolved_sale_item_day")
@IdClass(ResolvedSaleItemDay.Id.class)
class ResolvedSaleItemDay {
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @jakarta.persistence.Id
  @Column(name = "category_name", nullable = false)
  private String categoryName;

  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedSaleItemDay() {}

  ResolvedSaleItemDay(
      LocalDate tradingDate,
      String categoryName,
      BigDecimal quantity,
      BigDecimal amount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.categoryName = categoryName;
    this.quantity = quantity;
    this.amount = amount;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String categoryName() {
    return categoryName;
  }

  BigDecimal quantity() {
    return quantity;
  }

  BigDecimal amount() {
    return amount;
  }

  String authoritativeSource() {
    return authoritativeSource;
  }

  boolean hasConflict() {
    return hasConflict;
  }

  static class Id implements Serializable {
    private LocalDate tradingDate;
    private String categoryName;

    public Id() {}

    Id(LocalDate tradingDate, String categoryName) {
      this.tradingDate = tradingDate;
      this.categoryName = categoryName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(categoryName, other.categoryName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tradingDate, categoryName);
    }
  }
}
