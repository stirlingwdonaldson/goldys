package com.goldys.platform.canonical;

import com.goldys.platform.semantic.RecipeCatalog;
import com.goldys.platform.semantic.RecipeDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalRecipeDefinitionQuery {
  private final CanonicalRecipeDefinitionRepository definitions;
  private final CanonicalRecipeCatalogRepository catalogs;

  public CanonicalRecipeDefinitionQuery(
      CanonicalRecipeDefinitionRepository definitions, CanonicalRecipeCatalogRepository catalogs) {
    this.definitions = definitions;
    this.catalogs = catalogs;
  }

  @Transactional(readOnly = true)
  public Optional<RecipeDefinition> currentDefinition(int recipeId) {
    return definitions
        .findCurrent("recipe-definition:" + recipeId)
        .map(CanonicalRecipeDefinition::toValue);
  }

  @Transactional(readOnly = true)
  public List<RecipeDefinition> currentDefinitions() {
    return definitions.findAllCurrent().stream().map(CanonicalRecipeDefinition::toValue).toList();
  }

  @Transactional(readOnly = true)
  public Optional<RecipeCatalog> currentCatalog() {
    return catalogs.findAllCurrent().stream().findFirst().map(CanonicalRecipeCatalog::toValue);
  }
}
