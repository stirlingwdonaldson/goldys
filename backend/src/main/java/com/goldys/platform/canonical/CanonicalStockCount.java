package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One stocktake observation (product quantity on hand). Modelled for the inventory domain; no
 * ingestion source yet, so no service/ingest facade exists.
 */
@Entity
@Table(name = "canonical_stock_count")
class CanonicalStockCount extends BitemporalEntity {
  @Column(name = "counted_date", nullable = false, updatable = false)
  private LocalDate countedDate;

  @Column(name = "product_name_key", nullable = false, updatable = false, length = 512)
  private String productNameKey;

  @Column(name = "quantity_on_hand", nullable = false, updatable = false, precision = 14, scale = 4)
  private BigDecimal quantityOnHand;

  protected CanonicalStockCount() {}

  private CanonicalStockCount(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate countedDate,
      String productNameKey,
      BigDecimal quantityOnHand) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.countedDate = countedDate;
    this.productNameKey = productNameKey;
    this.quantityOnHand = quantityOnHand;
  }

  static CanonicalStockCount create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      LocalDate countedDate,
      String productNameKey,
      BigDecimal quantityOnHand) {
    return new CanonicalStockCount(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        countedDate,
        productNameKey,
        quantityOnHand);
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
}
