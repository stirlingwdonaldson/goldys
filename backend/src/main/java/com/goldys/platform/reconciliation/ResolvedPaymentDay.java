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
 * One date/payment-type's resolved payment totals. Disposable projection; reconstructed from {@code
 * canonical_payment} by {@link PaymentProjector}. Payments are single-source (Lightspeed), so there
 * is no multi-source conflict; rows are tagged {@code single}.
 */
@Entity
@Table(name = "resolved_payment_day")
@IdClass(ResolvedPaymentDay.Id.class)
class ResolvedPaymentDay {
  @jakarta.persistence.Id
  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @jakarta.persistence.Id
  @Column(name = "payment_type_name", nullable = false)
  private String paymentTypeName;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "tip", nullable = false)
  private BigDecimal tip;

  @Column(name = "payment_count", nullable = false)
  private long paymentCount;

  @Column(name = "resolution_type", nullable = false)
  private String resolutionType;

  @Column(name = "authoritative_source")
  private String authoritativeSource;

  @Column(name = "has_conflict", nullable = false)
  private boolean hasConflict;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected ResolvedPaymentDay() {}

  ResolvedPaymentDay(
      LocalDate tradingDate,
      String paymentTypeName,
      BigDecimal amount,
      BigDecimal tip,
      long paymentCount,
      String resolutionType,
      String authoritativeSource,
      boolean hasConflict,
      Instant resolvedAt) {
    this.tradingDate = tradingDate;
    this.paymentTypeName = paymentTypeName;
    this.amount = amount;
    this.tip = tip;
    this.paymentCount = paymentCount;
    this.resolutionType = resolutionType;
    this.authoritativeSource = authoritativeSource;
    this.hasConflict = hasConflict;
    this.resolvedAt = resolvedAt;
  }

  LocalDate tradingDate() {
    return tradingDate;
  }

  String paymentTypeName() {
    return paymentTypeName;
  }

  BigDecimal amount() {
    return amount;
  }

  BigDecimal tip() {
    return tip;
  }

  long paymentCount() {
    return paymentCount;
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
    private String paymentTypeName;

    public Id() {}

    Id(LocalDate tradingDate, String paymentTypeName) {
      this.tradingDate = tradingDate;
      this.paymentTypeName = paymentTypeName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(tradingDate, other.tradingDate)
          && Objects.equals(paymentTypeName, other.paymentTypeName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tradingDate, paymentTypeName);
    }
  }
}
