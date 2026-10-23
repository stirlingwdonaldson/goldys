package com.goldys.platform.canonical;

import com.goldys.platform.semantic.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "canonical_recipe_section")
class CanonicalRecipeSection extends BitemporalEntity {
  @Column(nullable = false)
  private int recipeId;

  @Column(nullable = false)
  private String section;

  @Column(nullable = false)
  private boolean successful;

  @Column(columnDefinition = "text")
  private String notice;

  private String endpoint;
  private String fetcher;
  private String captureRef;
  private UUID ingestionRunId;
  private String sha256;

  @ElementCollection
  @CollectionTable(
      name = "canonical_recipe_section_fields",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  @OrderColumn(name = "field_order")
  private List<RecipeFieldFact> fields;

  protected CanonicalRecipeSection() {}

  CanonicalRecipeSection(RecipeSection v, String ref, Instant now) {
    super(
        UUID.nameUUIDFromBytes(ref.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        v.evidence().sourceSystem(),
        ref,
        v.evidence().rawRecordId(),
        v.evidence().capturedAt(),
        now);
    recipeId = v.recipeId();
    section = v.section();
    successful = v.successful();
    notice = v.notice();
    endpoint = v.evidence().endpoint();
    fetcher = v.evidence().fetcher();
    captureRef = v.evidence().sourceRecordRef();
    ingestionRunId = v.evidence().ingestionRunId();
    sha256 = v.evidence().sha256();
    fields = v.fields().stream().map(RecipeFieldFact::new).toList();
  }

  RecipeSection toValue() {
    return new RecipeSection(
        recipeId,
        section,
        successful,
        notice,
        fields.stream().map(RecipeFieldFact::toValue).toList(),
        new RecipeEvidence(
            rawRecordId(),
            sourceSystem(),
            fetcher,
            endpoint,
            captureRef,
            validFrom(),
            ingestionRunId,
            sha256));
  }
}
