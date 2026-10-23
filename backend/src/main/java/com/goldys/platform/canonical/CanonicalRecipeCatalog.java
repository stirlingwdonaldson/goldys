package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeCatalog;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "canonical_recipe_catalog")
class CanonicalRecipeCatalog extends BitemporalEntity {
  @ElementCollection
  @CollectionTable(
      name = "canonical_recipe_catalog_evidence",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @Column(name = "raw_record_id", nullable = false)
  private List<UUID> evidence;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "canonical_recipe_catalog_folders",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @OrderColumn(name = "line_order")
  private List<RecipeFolderFact> folders;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "canonical_recipe_catalog_allocations",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @OrderColumn(name = "line_order")
  private List<RecipeOutletAllocationFact> allocations;

  protected CanonicalRecipeCatalog() {}

  CanonicalRecipeCatalog(
      RecipeCatalog value, UUID rawRecordId, List<UUID> evidence, Instant recordedAt) {
    super(
        UUID.nameUUIDFromBytes(("recipe-catalog").getBytes(StandardCharsets.UTF_8)),
        "CTB",
        "recipe-catalog",
        rawRecordId,
        value.observedAt(),
        recordedAt);
    this.evidence = List.copyOf(evidence);
    this.folders = value.folders().stream().map(RecipeFolderFact::from).toList();
    this.allocations = value.allocations().stream().map(RecipeOutletAllocationFact::from).toList();
  }

  RecipeCatalog toValue() {
    return new RecipeCatalog(
        folders.stream().map(RecipeFolderFact::toValue).toList(),
        allocations.stream().map(RecipeOutletAllocationFact::toValue).toList(),
        validFrom());
  }
}
