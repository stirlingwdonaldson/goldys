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
 * One product/day's authoritative resolved product-sales value. Disposable projection; {@code
 * quantitySold}/{@code amount} are null while the pair is unresolved.
 */
@Entity
@Table(name = "resolved_product_sales")
@IdClass(ResolvedProductSales.Id.class)
class ResolvedProductSales {
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @jakarta.persistence.Id
  @Column(name = "product_name_key", nullable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity_sold", precision = 14, scale = 4)
  private BigDecimal quantitySold;

  @Column(name = "amount", precision = 14, scale = 4)
  private BigDecimal amount;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedProductSales() {}

  ResolvedProductSales(
      LocalDate tradingDate,
      String productNameKey,
      BigDecimal quantitySold,
      BigDecimal amount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.productNameKey = productNameKey;
    this.quantitySold = quantitySold;
    this.amount = amount;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  /** Rebuild an existing projection row in place with a fresh resolution. */
  void replace(
      BigDecimal quantitySold,
      BigDecimal amount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.quantitySold = quantitySold;
    this.amount = amount;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String productNameKey() {
    return productNameKey;
  }

  BigDecimal quantitySold() {
    return quantitySold;
  }

  BigDecimal amount() {
    return amount;
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

  static class Id implements Serializable {
    private LocalDate tradingDate;
    private String productNameKey;

    public Id() {}

    Id(LocalDate tradingDate, String productNameKey) {
      this.tradingDate = tradingDate;
      this.productNameKey = productNameKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(productNameKey, other.productNameKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tradingDate, productNameKey);
    }
  }
}
