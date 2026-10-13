package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One normalized alias pair used for Lightspeed menu matching. */
@Entity
@Table(name = "product_alias")
class ProductAlias {
  @Id private UUID id;

  @Column(name = "canonical_name", nullable = false, updatable = false, length = 512)
  private String canonicalName;

  @Column(name = "alias", nullable = false, updatable = false, length = 512)
  private String alias;

  @Column(name = "source", length = 32)
  private String source;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ProductAlias() {}

  private ProductAlias(String canonicalName, String alias, String source, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName");
    this.alias = Objects.requireNonNull(alias, "alias");
    this.source = source;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  static ProductAlias create(String canonicalName, String alias, String source, Instant createdAt) {
    return new ProductAlias(canonicalName, alias, source, createdAt);
  }

  String canonicalName() {
    return canonicalName;
  }

  String alias() {
    return alias;
  }

  String source() {
    return source;
  }

  Instant createdAt() {
    return createdAt;
  }
}
