package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a Lightspeed product fact from the menu export. Fact fields are immutable; a
 * correction (a changed name, category, price, modifier flag or reporting group) closes this row
 * and inserts a successor sharing the same logical identity (the Lightspeed product id).
 */
@Entity
@Table(name = "canonical_product")
class CanonicalProduct extends BitemporalEntity {
  @Column(name = "product_id", nullable = false, updatable = false)
  private int productId;

  @Column(name = "product_name", updatable = false)
  private String productName;

  @Column(name = "category", updatable = false)
  private String category;

  @Column(name = "sell_price_inc_tax", updatable = false, precision = 14, scale = 4)
  private BigDecimal sellPriceIncTax;

  @Column(name = "is_modifier", updatable = false)
  private boolean isModifier;

  @Column(name = "reporting_group", updatable = false)
  private String reportingGroup;

  protected CanonicalProduct() {}

  private CanonicalProduct(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int productId,
      String productName,
      String category,
      BigDecimal sellPriceIncTax,
      boolean isModifier,
      String reportingGroup) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.productId = productId;
    this.productName = productName;
    this.category = category;
    this.sellPriceIncTax = sellPriceIncTax;
    this.isModifier = isModifier;
    this.reportingGroup = reportingGroup;
  }

  static CanonicalProduct create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      int productId,
      String productName,
      String category,
      BigDecimal sellPriceIncTax,
      boolean isModifier,
      String reportingGroup) {
    return new CanonicalProduct(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        productId,
        productName,
        category,
        sellPriceIncTax,
        isModifier,
        reportingGroup);
  }

  boolean sameFact(ProductInput input) {
    return productId == input.productId()
        && Objects.equals(productName, input.productName())
        && Objects.equals(category, input.category())
        && Objects.equals(sellPriceIncTax, input.sellPriceIncTax())
        && isModifier == input.isModifier()
        && Objects.equals(reportingGroup, input.reportingGroup());
  }

  int productId() {
    return productId;
  }

  String productName() {
    return productName;
  }

  String category() {
    return category;
  }

  BigDecimal sellPriceIncTax() {
    return sellPriceIncTax;
  }

  boolean isModifier() {
    return isModifier;
  }

  String reportingGroup() {
    return reportingGroup;
  }
}
