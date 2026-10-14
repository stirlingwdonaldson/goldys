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
 * One wastage observation (product quantity discarded). Fact fields are immutable; a correction
 * closes this row and inserts a successor sharing the same logical identity ({@code
 * (wastageRecordId, lineId)}).
 */
@Entity
@Table(name = "canonical_wastage")
class CanonicalWastage extends BitemporalEntity {
  @Column(name = "wastage_record_id", nullable = false, updatable = false)
  private int wastageRecordId;

  @Column(name = "line_id", nullable = false, updatable = false)
  private int lineId;

  @Column(name = "wastage_date", nullable = false, updatable = false)
  private LocalDate wastageDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantity;

  @Column(name = "cost", updatable = false, precision = 14, scale = 4)
  private BigDecimal cost;

  @Column(name = "reason", updatable = false)
  private String reason;

  @Column(name = "supplier_name", updatable = false, length = 512)
  private String supplierName;

  @Column(name = "unit_of_measurement_name", updatable = false, length = 64)
  private String unitOfMeasurementName;

  @Column(name = "unit_size", updatable = false, length = 64)
  private String unitSize;

  @Column(name = "recipe_id", updatable = false)
  private Integer recipeId;

  protected CanonicalWastage() {}

  private CanonicalWastage(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int wastageRecordId,
      int lineId,
      LocalDate wastageDate,
      String productNameKey,
      BigDecimal quantity,
      BigDecimal cost,
      String reason,
      String supplierName,
      String unitOfMeasurementName,
      String unitSize,
      Integer recipeId) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.wastageRecordId = wastageRecordId;
    this.lineId = lineId;
    this.wastageDate = wastageDate;
    this.productNameKey = productNameKey;
    this.quantity = quantity;
    this.cost = cost;
    this.reason = reason;
    this.supplierName = supplierName;
    this.unitOfMeasurementName = unitOfMeasurementName;
    this.unitSize = unitSize;
    this.recipeId = recipeId;
  }

  static CanonicalWastage create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int wastageRecordId,
      int lineId,
      LocalDate wastageDate,
      String productNameKey,
      BigDecimal quantity,
      BigDecimal cost,
      String reason,
      String supplierName,
      String unitOfMeasurementName,
      String unitSize,
      Integer recipeId) {
    return new CanonicalWastage(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        wastageRecordId,
        lineId,
        wastageDate,
        productNameKey,
        quantity,
        cost,
        reason,
        supplierName,
        unitOfMeasurementName,
        unitSize,
        recipeId);
  }

  boolean sameFact(WastageInput input) {
    return wastageRecordId == input.wastageRecordId()
        && lineId == input.lineId()
        && Objects.equals(wastageDate, input.wastageDate())
        && Objects.equals(productNameKey, input.productNameKey())
        && sameAmount(quantity, input.quantity())
        && sameAmount(cost, input.cost())
        && Objects.equals(reason, input.reason())
        && Objects.equals(supplierName, input.supplierName())
        && Objects.equals(unitOfMeasurementName, input.unitOfMeasurementName())
        && Objects.equals(unitSize, input.unitSize())
        && Objects.equals(recipeId, input.recipeId());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  int wastageRecordId() {
    return wastageRecordId;
  }

  int lineId() {
    return lineId;
  }

  LocalDate wastageDate() {
    return wastageDate;
  }

  String productNameKey() {
    return productNameKey;
  }

  BigDecimal quantity() {
    return quantity;
  }

  BigDecimal cost() {
    return cost;
  }

  String reason() {
    return reason;
  }

  String supplierName() {
    return supplierName;
  }

  String unitOfMeasurementName() {
    return unitOfMeasurementName;
  }

  String unitSize() {
    return unitSize;
  }

  Integer recipeId() {
    return recipeId;
  }
}
