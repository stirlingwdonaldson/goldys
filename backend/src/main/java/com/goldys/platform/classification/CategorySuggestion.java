package com.goldys.platform.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One LLM-drafted taxonomy suggestion awaiting staff review. The draft lives entirely in this table
 * (there is no status column): applying promotes its rows to {@code product_category} and clears
 * them. {@code run_id} is nullable so a staff-added suggestion (a category the model missed) needs
 * no run.
 */
@Entity
@Table(name = "category_suggestion")
class CategorySuggestion {

  @Id private UUID id;

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "group_name", length = 128)
  private String groupName;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "hint", length = 512)
  private String hint;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CategorySuggestion() {}

  private CategorySuggestion(
      UUID runId, String name, String groupName, int displayOrder, String hint) {
    this.id = UUID.randomUUID();
    this.runId = runId;
    this.name = Objects.requireNonNull(name, "name");
    this.groupName = groupName;
    this.displayOrder = displayOrder;
    this.hint = hint;
    this.createdAt = Instant.now();
  }

  static CategorySuggestion create(
      UUID runId, String name, String groupName, int displayOrder, String hint) {
    return new CategorySuggestion(runId, name, groupName, displayOrder, hint);
  }

  void rename(String newName) {
    this.name = Objects.requireNonNull(newName, "newName");
  }

  void regroup(String newGroupName) {
    this.groupName = newGroupName;
  }

  UUID id() {
    return id;
  }

  UUID runId() {
    return runId;
  }

  String name() {
    return name;
  }

  String groupName() {
    return groupName;
  }

  int displayOrder() {
    return displayOrder;
  }

  String hint() {
    return hint;
  }
}
