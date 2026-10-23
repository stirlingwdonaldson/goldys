package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalRecipeDefinitionQuery;
import com.goldys.platform.canonical.RecipeDefinitionRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuildable projection of complete recipe observations. */
@Service
public class RecipeDefinitionProjector {
  private final CanonicalRecipeDefinitionQuery canonical;
  private final ResolvedRecipeDefinitionRepository definitions;
  private final ResolvedRecipeCatalogRepository catalogs;

  public RecipeDefinitionProjector(
      CanonicalRecipeDefinitionQuery canonical,
      ResolvedRecipeDefinitionRepository definitions,
      ResolvedRecipeCatalogRepository catalogs) {
    this.canonical = canonical;
    this.definitions = definitions;
    this.catalogs = catalogs;
  }

  @EventListener
  @Transactional
  public void on(RecipeDefinitionRecorded event) {
    if (event.recipeId() == null) {
      canonical
          .currentCatalog()
          .ifPresent(value -> catalogs.save(new ResolvedRecipeCatalog(value)));
    } else {
      canonical
          .currentDefinition(event.recipeId())
          .ifPresent(value -> definitions.save(new ResolvedRecipeDefinition(value)));
    }
  }

  @Transactional
  public void recomputeAll() {
    definitions.deleteAllInBatch();
    definitions.saveAll(
        canonical.currentDefinitions().stream().map(ResolvedRecipeDefinition::new).toList());
    catalogs.deleteAllInBatch();
    canonical.currentCatalog().ifPresent(value -> catalogs.save(new ResolvedRecipeCatalog(value)));
  }
}
