package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a canonical sale item.
 *
 * <p>Fact fields ({@code itemName}, {@code quantitySold}, {@code amount}) are immutable; a
 * correction closes this row and inserts a successor sharing the same logical identity.
 */
@Entity
@Table(name = "canonical_sale_item")
class CanonicalSaleItem extends BitemporalEntity {
  @Column(name = "item_name", updatable = false, length = 255)
  private String itemName;

  @Column(name = "quantity_sold", updatable = false)
  private Integer quantitySold;

  @Column(name = "amount", updatable = false, precision = 38, scale = 2)
  private BigDecimal amount;

  protected CanonicalSaleItem() {}

  private CanonicalSaleItem(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String itemName,
      Integer quantitySold,
      BigDecimal amount) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.itemName = itemName;
    this.quantitySold = quantitySold;
    this.amount = amount;
  }

  static CanonicalSaleItem create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      String itemName,
      Integer quantitySold,
      BigDecimal amount) {
    return new CanonicalSaleItem(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        itemName,
        quantitySold,
        amount);
  }

  /** Whether this version describes the same normalized fact as the incoming candidate. */
  boolean sameFact(SaleItemInput input) {
    return Objects.equals(itemName, input.itemName())
        && Objects.equals(quantitySold, input.quantitySold())
        && sameAmount(amount, input.amount());
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  String itemName() {
    return itemName;
  }

  Integer quantitySold() {
    return quantitySold;
  }

  BigDecimal amount() {
    return amount;
  }
}
