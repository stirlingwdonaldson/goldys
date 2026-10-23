package com.goldys.platform.semantic;

import java.time.Instant;
import java.util.UUID;

/** Exact source capture supporting a recipe section or calculation input. */
public record RecipeEvidence(
    UUID rawRecordId,
    String sourceSystem,
    String fetcher,
    String endpoint,
    String sourceRecordRef,
    Instant capturedAt,
    UUID ingestionRunId,
    String sha256,
    java.util.List<String> entityRefs) {
  public RecipeEvidence {
    entityRefs = entityRefs == null ? java.util.List.of() : java.util.List.copyOf(entityRefs);
  }

  public RecipeEvidence(
      UUID rawRecordId,
      String sourceSystem,
      String fetcher,
      String endpoint,
      String sourceRecordRef,
      Instant capturedAt,
      UUID ingestionRunId,
      String sha256) {
    this(
        rawRecordId,
        sourceSystem,
        fetcher,
        endpoint,
        sourceRecordRef,
        capturedAt,
        ingestionRunId,
        sha256,
        java.util.List.of());
  }

  public RecipeEvidence forEntity(String ref) {
    return new RecipeEvidence(
        rawRecordId,
        sourceSystem,
        fetcher,
        endpoint,
        sourceRecordRef,
        capturedAt,
        ingestionRunId,
        sha256,
        java.util.List.of(ref));
  }
}
