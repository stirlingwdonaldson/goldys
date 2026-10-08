package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One wastage observation (product quantity discarded). Modelled for the inventory domain; no
 * ingestion source yet, so no service/ingest facade exists.
 */
@Entity
@Table(name = "canonical_wastage")
class CanonicalWastage extends BitemporalEntity {
  @Column(name = "wastage_date", nullable = false, updatable = false)
  private LocalDate wastageDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantity;

  @Column(name = "reason", updatable = false)
  private String reason;

  protected CanonicalWastage() {}

  private CanonicalWastage(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate wastageDate,
      String productNameKey,
      BigDecimal quantity,
      String reason) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.wastageDate = wastageDate;
    this.productNameKey = productNameKey;
    this.quantity = quantity;
    this.reason = reason;
  }

  static CanonicalWastage create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate wastageDate,
      String productNameKey,
      BigDecimal quantity,
      String reason) {
    return new CanonicalWastage(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        wastageDate,
        productNameKey,
        quantity,
        reason);
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

  String reason() {
    return reason;
  }
}
