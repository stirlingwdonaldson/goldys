package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
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

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "source_product_ref", updatable = false, length = 512)
  private String sourceProductRef;

  String sourceProductRef() {
    return sourceProductRef;
  }

  static CanonicalProductSales qualified(
      UUID logicalId, ProductSalesInput input, String ref, Instant at) {
    CanonicalProductSales row =
        create(
            logicalId,
            input.sourceSystem(),
            ref,
            input.rawRecordId(),
            at,
            at,
            input.tradingDate(),
            input.productNameKey(),
            input.quantitySold(),
            input.amount(),
            input.stockCode());
    row.sourceProductRef = input.sourceProductRef();
    return row;
  }

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
      BigDecimal amount,
      String stockCode) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.productNameKey = productNameKey;
    this.quantitySold = quantitySold;
    this.amount = amount;
    this.stockCode = stockCode;
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
      BigDecimal amount,
      String stockCode) {
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
        amount,
        stockCode);
  }

  boolean sameFact(ProductSalesInput input) {
    return Objects.equals(productNameKey, input.productNameKey())
        && Objects.equals(sourceProductRef, input.sourceProductRef())
        && same(amount, input.amount())
        && same(quantitySold, input.quantitySold())
        && Objects.equals(stockCode, input.stockCode());
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

  String stockCode() {
    return stockCode;
  }
}
