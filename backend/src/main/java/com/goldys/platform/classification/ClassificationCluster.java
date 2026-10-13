package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * A disposable dendrogram artifact from a classification run. Leaves are products (one per {@code
 * product_name_key}); internal nodes are agglomerative merges with {@code product_name_key} null.
 * {@code merge_height} and {@code size} are null until the Python service reports them.
 */
@Entity
@Table(name = "classification_cluster")
class ClassificationCluster {

  @Id private UUID id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "is_leaf", nullable = false)
  private boolean isLeaf;

  @Column(name = "product_name_key", length = 512)
  private String productNameKey;

  @Column(name = "merge_height")
  private Double mergeHeight;

  @Column(name = "size")
  private Integer size;

  protected ClassificationCluster() {}

  ClassificationCluster(
      UUID id,
      UUID runId,
      UUID parentId,
      boolean isLeaf,
      String productNameKey,
      Double mergeHeight,
      Integer size) {
    this.id = Objects.requireNonNull(id, "id");
    this.runId = Objects.requireNonNull(runId, "runId");
    this.parentId = parentId;
    this.isLeaf = isLeaf;
    this.productNameKey = productNameKey;
    this.mergeHeight = mergeHeight;
    this.size = size;
  }

  UUID id() {
    return id;
  }

  UUID runId() {
    return runId;
  }

  UUID parentId() {
    return parentId;
  }

  boolean isLeaf() {
    return isLeaf;
  }

  String productNameKey() {
    return productNameKey;
  }

  Double mergeHeight() {
    return mergeHeight;
  }

  Integer size() {
    return size;
  }
}
