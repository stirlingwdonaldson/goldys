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
 * One version of a stocktake-header fact. Fact fields are immutable; a correction closes this row
 * and inserts a successor sharing the same logical identity (the stocktake id).
 */
@Entity
@Table(name = "canonical_stocktake")
class CanonicalStocktake extends BitemporalEntity {
  @Column(name = "stocktake_id", nullable = false, updatable = false)
  private int stocktakeId;

  @Column(name = "stocktake_date", nullable = false, updatable = false)
  private LocalDate stocktakeDate;

  @Column(name = "description", updatable = false, length = 512)
  private String description;

  @Column(name = "outlet_name", updatable = false, length = 255)
  private String outletName;

  @Column(name = "total_stock_value", updatable = false, precision = 14, scale = 4)
  private BigDecimal totalStockValue;

  @Column(name = "is_partial", updatable = false)
  private Boolean isPartial;

  @Column(name = "is_locked", updatable = false)
  private Boolean isLocked;

  protected CanonicalStocktake() {}

  private CanonicalStocktake(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int stocktakeId,
      LocalDate stocktakeDate,
      String description,
      String outletName,
      BigDecimal totalStockValue,
      Boolean isPartial,
      Boolean isLocked) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.stocktakeId = stocktakeId;
    this.stocktakeDate = stocktakeDate;
    this.description = description;
    this.outletName = outletName;
    this.totalStockValue = totalStockValue;
    this.isPartial = isPartial;
    this.isLocked = isLocked;
  }

  static CanonicalStocktake create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int stocktakeId,
      LocalDate stocktakeDate,
      String description,
      String outletName,
      BigDecimal totalStockValue,
      Boolean isPartial,
      Boolean isLocked) {
    return new CanonicalStocktake(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        stocktakeId,
        stocktakeDate,
        description,
        outletName,
        totalStockValue,
        isPartial,
        isLocked);
  }

  boolean sameFact(StocktakeInput input) {
    return stocktakeId == input.stocktakeId()
        && Objects.equals(stocktakeDate, input.stocktakeDate())
        && Objects.equals(description, input.description())
        && Objects.equals(outletName, input.outletName())
        && sameAmount(totalStockValue, input.totalStockValue())
        && Objects.equals(isPartial, input.isPartial())
        && Objects.equals(isLocked, input.isLocked());
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

  LocalDate stocktakeDate() {
    return stocktakeDate;
  }

  String description() {
    return description;
  }

  String outletName() {
    return outletName;
  }

  BigDecimal totalStockValue() {
    return totalStockValue;
  }

  Boolean isPartial() {
    return isPartial;
  }

  Boolean isLocked() {
    return isLocked;
  }
}
