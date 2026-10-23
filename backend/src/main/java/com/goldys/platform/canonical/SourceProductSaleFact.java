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
 * discount, tax, unit price, profit) that have no universal canonical home. Keyed by (source, stock
 * code, trading date, source group). Never projected into a resolved view — it is a source
 * observation, not a reconciled business fact.
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

  @Column(name = "food_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal foodCost;

  @Column(name = "total_food_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal totalFoodCost;

  @Column(name = "profit", updatable = false, precision = 14, scale = 4)
  private BigDecimal profit;

  @Column(name = "discounted_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal discountedAmount;

  @Column(name = "tax_amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal taxAmount;

  @Column(name = "unit_price", updatable = false, precision = 14, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "unique_group_id", updatable = false)
  private String uniqueGroupId;

  @Column(name = "quantity_sold", updatable = false, precision = 14, scale = 4)
  private BigDecimal quantitySold;

  @Column(name = "amount", updatable = false, precision = 14, scale = 4)
  private BigDecimal amount;

  @Column(name = "product_name", updatable = false, length = 512)
  private String productName;

  @Column(name = "site_number", updatable = false)
  private Integer siteNumber;

  void captureSourceFields(SourceProductSaleFactInput input) {
    quantitySold = input.quantitySold();
    amount = input.amount();
    productName = input.productName();
    siteNumber = input.siteNumber();
  }

  Integer siteNumber() {
    return siteNumber;
  }

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
      BigDecimal foodCost,
      BigDecimal totalFoodCost,
      BigDecimal profit,
      BigDecimal discountedAmount,
      BigDecimal taxAmount,
      BigDecimal unitPrice,
      String uniqueGroupId) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.tradingDate = tradingDate;
    this.stockCode = stockCode;
    this.costPerUnit = costPerUnit;
    this.foodCost = foodCost;
    this.totalFoodCost = totalFoodCost;
    this.profit = profit;
    this.discountedAmount = discountedAmount;
    this.taxAmount = taxAmount;
    this.unitPrice = unitPrice;
    this.uniqueGroupId = uniqueGroupId;
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
      BigDecimal foodCost,
      BigDecimal totalFoodCost,
      BigDecimal profit,
      BigDecimal discountedAmount,
      BigDecimal taxAmount,
      BigDecimal unitPrice,
      String uniqueGroupId) {
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
        foodCost,
        totalFoodCost,
        profit,
        discountedAmount,
        taxAmount,
        unitPrice,
        uniqueGroupId);
  }

  boolean sameFact(SourceProductSaleFactInput input) {
    return same(quantitySold, input.quantitySold())
        && same(amount, input.amount())
        && Objects.equals(productName, input.productName())
        && Objects.equals(siteNumber, input.siteNumber())
        && Objects.equals(stockCode, input.stockCode())
        && Objects.equals(uniqueGroupId, input.uniqueGroupId())
        && same(costPerUnit, input.costPerUnit())
        && same(foodCost, input.foodCost())
        && same(totalFoodCost, input.totalFoodCost())
        && same(profit, input.profit())
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

  BigDecimal foodCost() {
    return foodCost;
  }

  BigDecimal totalFoodCost() {
    return totalFoodCost;
  }

  BigDecimal profit() {
    return profit;
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

  String uniqueGroupId() {
    return uniqueGroupId;
  }
}
