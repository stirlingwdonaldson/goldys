package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a supplier-scoped stock item: a purchasable item identified by a supplier plus a
 * stock code. Distinct from a sold product — a purchased ingredient is not the same entity as a
 * sold menu item. {@code sourceProductId} links the stock item to its source product when the
 * source evidence connects them; it is null when no such link is observed.
 */
@Entity
@Table(name = "stock_item")
class StockItem extends BitemporalEntity {
  @Column(name = "supplier_logical_id", updatable = false)
  private UUID supplierLogicalId;

  @Column(name = "stock_code", updatable = false)
  private String stockCode;

  @Column(name = "name", updatable = false)
  private String name;

  @Column(name = "source_product_id", updatable = false)
  private UUID sourceProductId;

  protected StockItem() {}

  private StockItem(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID supplierLogicalId,
      String stockCode,
      String name,
      UUID sourceProductId) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.supplierLogicalId = supplierLogicalId;
    this.stockCode = stockCode;
    this.name = name;
    this.sourceProductId = sourceProductId;
  }

  static StockItem create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID supplierLogicalId,
      String stockCode,
      String name,
      UUID sourceProductId) {
    return new StockItem(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        supplierLogicalId,
        stockCode,
        name,
        sourceProductId);
  }

  boolean sameFact(StockItemInput input) {
    return Objects.equals(supplierLogicalId, input.supplierLogicalId())
        && Objects.equals(stockCode, input.stockCode())
        && Objects.equals(name, input.name())
        && Objects.equals(sourceProductId, input.sourceProductId());
  }

  UUID supplierLogicalId() {
    return supplierLogicalId;
  }

  String stockCode() {
    return stockCode;
  }

  String name() {
    return name;
  }

  UUID sourceProductId() {
    return sourceProductId;
  }
}
