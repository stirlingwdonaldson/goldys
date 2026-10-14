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
 * One stocktake observation (product quantity on hand). Fact fields are immutable; a correction
 * closes this row and inserts a successor sharing the same logical identity ({@code
 * (stocktakeId, lineId)}).
 */
@Entity
@Table(name = "canonical_stock_count")
class CanonicalStockCount extends BitemporalEntity {
  @Column(name = "stocktake_id", nullable = false, updatable = false)
  private int stocktakeId;

  @Column(name = "line_id", nullable = false, updatable = false)
  private int lineId;

  @Column(name = "counted_date", nullable = false, updatable = false)
  private LocalDate countedDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity_on_hand", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantityOnHand;

  @Column(name = "stock_id", updatable = false)
  private Integer stockId;

  @Column(name = "stock_code", updatable = false, length = 255)
  private String stockCode;

  @Column(name = "unit_of_measurement_name", updatable = false, length = 64)
  private String unitOfMeasurementName;

  @Column(name = "unit_cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "line_value", updatable = false, precision = 14, scale = 4)
  private BigDecimal lineValue;

  @Column(name = "supplier_name", updatable = false, length = 512)
  private String supplierName;

  @Column(name = "area_name", updatable = false, length = 255)
  private String areaName;

  @Column(name = "recipe_id", updatable = false)
  private Integer recipeId;

  protected CanonicalStockCount() {}

  private CanonicalStockCount(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int stocktakeId,
      int lineId,
      LocalDate countedDate,
      String productNameKey,
      BigDecimal quantityOnHand,
      Integer stockId,
      String stockCode,
      String unitOfMeasurementName,
      BigDecimal unitCost,
      BigDecimal lineValue,
      String supplierName,
      String areaName,
      Integer recipeId) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.stocktakeId = stocktakeId;
    this.lineId = lineId;
    this.countedDate = countedDate;
    this.productNameKey = productNameKey;
    this.quantityOnHand = quantityOnHand;
    this.stockId = stockId;
    this.stockCode = stockCode;
    this.unitOfMeasurementName = unitOfMeasurementName;
    this.unitCost = unitCost;
    this.lineValue = lineValue;
    this.supplierName = supplierName;
    this.areaName = areaName;
    this.recipeId = recipeId;
  }

  static CanonicalStockCount create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int stocktakeId,
      int lineId,
      LocalDate countedDate,
      String productNameKey,
      BigDecimal quantityOnHand,
      Integer stockId,
      String stockCode,
      String unitOfMeasurementName,
      BigDecimal unitCost,
      BigDecimal lineValue,
      String supplierName,
      String areaName,
      Integer recipeId) {
    return new CanonicalStockCount(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        stocktakeId,
        lineId,
        countedDate,
        productNameKey,
        quantityOnHand,
        stockId,
        stockCode,
        unitOfMeasurementName,
        unitCost,
        lineValue,
        supplierName,
        areaName,
        recipeId);
  }

  boolean sameFact(StockCountInput input) {
    return stocktakeId == input.stocktakeId()
        && lineId == input.lineId()
        && Objects.equals(countedDate, input.countedDate())
        && Objects.equals(productNameKey, input.productNameKey())
        && sameAmount(quantityOnHand, input.quantityOnHand())
        && Objects.equals(stockId, input.stockId())
        && Objects.equals(stockCode, input.stockCode())
        && Objects.equals(unitOfMeasurementName, input.unitOfMeasurementName())
        && sameAmount(unitCost, input.unitCost())
        && sameAmount(lineValue, input.lineValue())
        && Objects.equals(supplierName, input.supplierName())
        && Objects.equals(areaName, input.areaName())
        && Objects.equals(recipeId, input.recipeId());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  int stocktakeId() {
    return stocktakeId;
  }

  int lineId() {
    return lineId;
  }

  LocalDate countedDate() {
    return countedDate;
  }

  String productNameKey() {
    return productNameKey;
  }

  BigDecimal quantityOnHand() {
    return quantityOnHand;
  }

  Integer stockId() {
    return stockId;
  }

  String stockCode() {
    return stockCode;
  }

  String unitOfMeasurementName() {
    return unitOfMeasurementName;
  }

  BigDecimal unitCost() {
    return unitCost;
  }

  BigDecimal lineValue() {
    return lineValue;
  }

  String supplierName() {
    return supplierName;
  }

  String areaName() {
    return areaName;
  }

  Integer recipeId() {
    return recipeId;
  }
}
