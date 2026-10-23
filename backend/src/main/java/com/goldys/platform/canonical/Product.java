package com.goldys.platform.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The internal business-product hub: a stable identity plus a display name. Source products map
 * onto it; the hub survives source renames and re-keying. The name is our canonical display name,
 * not an identity key — mapping decisions live in {@code product_mapping}, never here.
 */
@Entity
@Table(name = "product")
class Product {
  @Id private UUID id;

  @Column(name = "name", nullable = false, updatable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Product() {}

  private Product(UUID id, String name, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
  }

  static Product create(UUID id, String name, Instant createdAt) {
    return new Product(id, name, createdAt);
  }

  UUID id() {
    return id;
  }

  String name() {
    return name;
  }

  Instant createdAt() {
    return createdAt;
  }
}
