package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of a source's per-product daily sales total. */
@Entity
@Table(name = "canonical_product_sales")
class CanonicalProductSales extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity_sold", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantitySold;

  @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal amount;

  protected CanonicalProductSales() {}

  private CanonicalProductSales(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      String productNameKey,
      BigDecimal quantitySold,
      BigDecimal amount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.productNameKey = productNameKey;
    this.quantitySold = quantitySold;
    this.amount = amount;
  }

  static CanonicalProductSales create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      String productNameKey,
      BigDecimal quantitySold,
      BigDecimal amount) {
    return new CanonicalProductSales(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        tradingDate,
        productNameKey,
        quantitySold,
        amount);
  }

  boolean sameFact(ProductSalesInput input) {
    return same(amount, input.amount()) && same(quantitySold, input.quantitySold());
  }

  private static boolean same(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
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
}
