package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A curated product category: one label per row with an optional {@code group} parent, so the
 * taxonomy can be viewed flat (one level) or grouped (two levels). Staff-authored operational
 * state, not a bitemporal canonical fact.
 */
@Entity
@Table(name = "product_category")
class ProductCategory {

  @Id private UUID id;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "group_name", length = 128)
  private String groupName;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "description", length = 512)
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProductCategory() {}

  private ProductCategory(String name, String groupName, int displayOrder, String description) {
    this.id = UUID.randomUUID();
    this.name = Objects.requireNonNull(name, "name");
    this.groupName = groupName;
    this.displayOrder = displayOrder;
    this.description = description;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static ProductCategory create(
      String name, String groupName, int displayOrder, String description) {
    return new ProductCategory(name, groupName, displayOrder, description);
  }

  /** Renames this category. The caller is responsible for enforcing global name uniqueness. */
  public void rename(String newName) {
    this.name = Objects.requireNonNull(newName, "newName");
    this.updatedAt = Instant.now();
  }

  /** Sets (or, with {@code null}, clears) the parent group of this category. */
  public void regroup(String newGroupName) {
    this.groupName = newGroupName;
    this.updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String groupName() {
    return groupName;
  }

  public int displayOrder() {
    return displayOrder;
  }

  public String description() {
    return description;
  }
}
