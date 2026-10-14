package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source-native product-day measure: fields CTB supplies (source cost, food cost,
 * discount, tax, unit price) that have no universal canonical home. Keyed by (source, stock code,
 * trading date). Never projected into a resolved view — it is a source observation, not a
 * reconciled business fact.
 */
@Entity
@Table(name = "source_product_sale_fact")
class SourceProductSaleFact extends BitemporalEntity {
  @Column(name = "trading_date", nullable = false, updatable = false)
  private LocalDate tradingDate;

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "cost_per_unit", updatable = false, precision = 14, scale = 4)
  private BigDecimal costPerUnit;

  @Column(name = "total_food_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal totalFoodCost;

  @Column(name = "discounted_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal discountedAmount;

  @Column(name = "tax_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal taxAmount;

  @Column(name = "unit_price", updatable = false, precision = 14, scale = 4)
  private BigDecimal unitPrice;

  protected SourceProductSaleFact() {}

  private SourceProductSaleFact(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      String stockCode,
      BigDecimal costPerUnit,
      BigDecimal totalFoodCost,
      BigDecimal discountedAmount,
      BigDecimal taxAmount,
      BigDecimal unitPrice) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.stockCode = stockCode;
    this.costPerUnit = costPerUnit;
    this.totalFoodCost = totalFoodCost;
    this.discountedAmount = discountedAmount;
    this.taxAmount = taxAmount;
    this.unitPrice = unitPrice;
  }

  static SourceProductSaleFact create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate tradingDate,
      String stockCode,
      BigDecimal costPerUnit,
      BigDecimal totalFoodCost,
      BigDecimal discountedAmount,
      BigDecimal taxAmount,
      BigDecimal unitPrice) {
    return new SourceProductSaleFact(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        tradingDate,
        stockCode,
        costPerUnit,
        totalFoodCost,
        discountedAmount,
        taxAmount,
        unitPrice);
  }

  boolean sameFact(SourceProductSaleFactInput input) {
    return Objects.equals(stockCode, input.stockCode())
        && same(costPerUnit, input.costPerUnit())
        && same(totalFoodCost, input.totalFoodCost())
        && same(discountedAmount, input.discountedAmount())
        && same(taxAmount, input.taxAmount())
        && same(unitPrice, input.unitPrice());
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

  String stockCode() {
    return stockCode;
  }

  BigDecimal costPerUnit() {
    return costPerUnit;
  }

  BigDecimal totalFoodCost() {
    return totalFoodCost;
  }

  BigDecimal discountedAmount() {
    return discountedAmount;
  }

  BigDecimal taxAmount() {
    return taxAmount;
  }

  BigDecimal unitPrice() {
    return unitPrice;
  }
}
