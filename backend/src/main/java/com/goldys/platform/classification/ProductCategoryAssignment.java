package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The resolved product→category mapping: one row per {@code product_name_key}, derived by Jev and
 * overridable by staff. Keyed directly on {@code product_name_key} (no surrogate id), so a manual
 * move is an in-place update rather than a second row.
 */
@Entity
@Table(name = "product_category_assignment")
class ProductCategoryAssignment {

  @Id
  @Column(name = "product_name_key", nullable = false, length = 512)
  private String productNameKey;

  @Column(name = "product_category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "confidence", precision = 6, scale = 4)
  private BigDecimal confidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private AssignmentSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AssignmentStatus status;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProductCategoryAssignment() {}

  private ProductCategoryAssignment(String productNameKey, Instant now) {
    this.productNameKey = Objects.requireNonNull(productNameKey, "productNameKey");
    this.categoryId = null;
    this.confidence = null;
    this.source = null;
    this.status = AssignmentStatus.UNASSIGNED;
    this.assignedAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public static ProductCategoryAssignment create(String productNameKey, Instant now) {
    return new ProductCategoryAssignment(productNameKey, now);
  }

  /** Records a confident assignment from the given source. */
  public void markAssigned(
      UUID categoryId, BigDecimal confidence, AssignmentSource source, Instant now) {
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
    this.confidence = confidence;
    this.source = Objects.requireNonNull(source, "source");
    this.status = AssignmentStatus.ASSIGNED;
    this.assignedAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  /** Marks a low-confidence or abstained classification as needing staff review. */
  public void markNeedsReview(BigDecimal confidence, Instant now) {
    this.confidence = confidence;
    this.status = AssignmentStatus.NEEDS_REVIEW;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  /** Reverts this assignment to unassigned (clears category, confidence and source). */
  public void markUnassigned(Instant now) {
    this.categoryId = null;
    this.confidence = null;
    this.source = null;
    this.status = AssignmentStatus.UNASSIGNED;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public String productNameKey() {
    return productNameKey;
  }

  public UUID categoryId() {
    return categoryId;
  }

  public BigDecimal confidence() {
    return confidence;
  }

  public AssignmentSource source() {
    return source;
  }

  public AssignmentStatus status() {
    return status;
  }

  public Instant assignedAt() {
    return assignedAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
