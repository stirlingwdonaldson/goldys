package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a source-scoped product identity. The identity is the source's own code
 * (Lightspeed product id, CTB stock code); the name is an attribute that may change, so a rename
 * supersedes this row and inserts a successor sharing the same {@code logicalEntityId}. Two
 * products that share a name but have different source codes stay distinct — the name never implies
 * identity.
 */
@Entity
@Table(name = "source_product")
class SourceProduct extends BitemporalEntity {
  @Column(name = "source_product_id", updatable = false)
  private Integer sourceProductId;

  @Column(name = "name", updatable = false)
  private String name;

  @Column(name = "sku", updatable = false)
  private String sku;

  @Column(name = "category", updatable = false)
  private String category;

  protected SourceProduct() {}

  private SourceProduct(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Integer sourceProductId,
      String name,
      String sku,
      String category) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.sourceProductId = sourceProductId;
    this.name = name;
    this.sku = sku;
    this.category = category;
  }

  static SourceProduct create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      Integer sourceProductId,
      String name,
      String sku,
      String category) {
    return new SourceProduct(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        sourceProductId,
        name,
        sku,
        category);
  }

  boolean sameFact(SourceProductInput input) {
    return Objects.equals(sourceProductId, input.sourceProductId())
        && Objects.equals(name, input.name())
        && Objects.equals(sku, input.sku())
        && Objects.equals(category, input.category());
  }

  Integer sourceProductId() {
    return sourceProductId;
  }

  String name() {
    return name;
  }

  String sku() {
    return sku;
  }

  String category() {
    return category;
  }
}
