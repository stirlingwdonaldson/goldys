package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One version of a source-product → business-product mapping. A mapping can change over time (a
 * product is re-classified, a name match is later accepted or rejected), so it is bitemporal:
 * superseding a mapping closes it and inserts a successor. {@code confidence} records how the link
 * was made ({@code SOURCE_CODE}, {@code NAME}, {@code MANUAL}) and {@code reviewStatus} records
 * whether it is auto-suggested or has been accepted/rejected by a human.
 */
@Entity
@Table(name = "product_mapping")
class ProductMapping extends BitemporalEntity {
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "confidence", nullable = false, updatable = false, length = 16)
  private String confidence;

  @Column(name = "review_status", nullable = false, updatable = false, length = 16)
  private String reviewStatus;

  protected ProductMapping() {}

  private ProductMapping(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID productId,
      String confidence,
      String reviewStatus) {
    super(logicalEntityId, sourceSystem, sourceRecordRef, rawRecordId, validFrom, recordedAt);
    this.productId = productId;
    this.confidence = confidence;
    this.reviewStatus = reviewStatus;
  }

  static ProductMapping create(
      UUID logicalEntityId,
      String sourceSystem,
      String sourceRecordRef,
      UUID rawRecordId,
      Instant validFrom,
      Instant recordedAt,
      UUID productId,
      String confidence,
      String reviewStatus) {
    return new ProductMapping(
        logicalEntityId,
        sourceSystem,
        sourceRecordRef,
        rawRecordId,
        validFrom,
        recordedAt,
        productId,
        confidence,
        reviewStatus);
  }

  boolean sameFact(ProductMappingInput input) {
    return productId.equals(input.productId())
        && confidence.equals(input.confidence())
        && reviewStatus.equals(input.reviewStatus());
  }

  UUID productId() {
    return productId;
  }

  String confidence() {
    return confidence;
  }

  String reviewStatus() {
    return reviewStatus;
  }
}
