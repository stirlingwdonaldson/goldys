package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * A disposable 2-D coordinate for the embedding-space scatter, one row per product per run. The
 * coordinates are a PCA projection of the embeddings produced for {@code run_id}.
 */
@Entity
@Table(name = "classification_projection")
@IdClass(ClassificationProjection.Id.class)
class ClassificationProjection {

  @jakarta.persistence.Id
  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @jakarta.persistence.Id
  @Column(name = "product_name_key", nullable = false, length = 512)
  private String productNameKey;

  @Column(name = "x", nullable = false)
  private double x;

  @Column(name = "y", nullable = false)
  private double y;

  protected ClassificationProjection() {}

  ClassificationProjection(UUID runId, String productNameKey, double x, double y) {
    this.runId = Objects.requireNonNull(runId, "runId");
    this.productNameKey = Objects.requireNonNull(productNameKey, "productNameKey");
    this.x = x;
    this.y = y;
  }

  UUID runId() {
    return runId;
  }

  String productNameKey() {
    return productNameKey;
  }

  double x() {
    return x;
  }

  double y() {
    return y;
  }

  static class Id implements Serializable {
    private UUID runId;
    private String productNameKey;

    public Id() {}

    Id(UUID runId, String productNameKey) {
      this.runId = runId;
      this.productNameKey = productNameKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(runId, other.runId)
          && Objects.equals(productNameKey, other.productNameKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(runId, productNameKey);
    }
  }
}
