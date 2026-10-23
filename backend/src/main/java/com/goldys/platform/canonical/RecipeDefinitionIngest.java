package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeCatalog;
import com.goldys.platform.semantic.RecipeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complete observations replace all child lines together; partial fetches never call this facade.
 */
@Service
public class RecipeDefinitionIngest {
  private final CanonicalRecipeDefinitionRepository definitions;
  private final CanonicalRecipeCatalogRepository catalogs;
  private final ApplicationEventPublisher events;

  public RecipeDefinitionIngest(
      CanonicalRecipeDefinitionRepository definitions,
      CanonicalRecipeCatalogRepository catalogs,
      ApplicationEventPublisher events) {
    this.definitions = definitions;
    this.catalogs = catalogs;
    this.events = events;
  }

  @Transactional
  public void record(RecipeDefinition value, UUID headerRawId, UUID stocksRawId, UUID usagesRawId) {
    String ref = "recipe-definition:" + value.header().recipeId();
    var current = definitions.lockCurrent(ref);
    // Replaying the same evidence or an older capture must not replace newer knowledge or make
    // old facts appear fresh. New observations retain their actual capture time as valid time.
    if (current.isPresent() && !value.observedAt().isAfter(current.get().validFrom())) return;
    Instant now = Instant.now();
    current.ifPresent(
        row -> {
          row.supersede(now);
          definitions.saveAndFlush(row);
        });
    definitions.saveAndFlush(
        new CanonicalRecipeDefinition(value, headerRawId, stocksRawId, usagesRawId, now));
    events.publishEvent(new RecipeDefinitionRecorded(value.header().recipeId()));
  }

  @Transactional
  public void recordCatalog(RecipeCatalog value, List<UUID> evidence) {
    if (evidence.isEmpty()) throw new IllegalArgumentException("Catalog requires raw evidence");
    var current = catalogs.lockCurrent("recipe-catalog");
    if (current.isPresent() && !value.observedAt().isAfter(current.get().validFrom())) return;
    Instant now = Instant.now();
    current.ifPresent(
        row -> {
          row.supersede(now);
          catalogs.saveAndFlush(row);
        });
    catalogs.saveAndFlush(new CanonicalRecipeCatalog(value, evidence.getFirst(), evidence, now));
    events.publishEvent(new RecipeDefinitionRecorded(null));
  }
}
