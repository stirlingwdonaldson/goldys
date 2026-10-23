package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.*;
import org.springframework.stereotype.Service;

@Service
public class RecipeCompositionReportingService {
  private final RecipeCompositionQuery query;

  @org.springframework.beans.factory.annotation.Autowired
  private ConnectorHealthQuery connectorHealth;

  @org.springframework.beans.factory.annotation.Value("${ctb.recipes.enabled:false}")
  private boolean collectionEnabled;

  private final PermissionService permissions;

  public RecipeCompositionReportingService(
      RecipeCompositionQuery query, PermissionService permissions) {
    this.query = query;
    this.permissions = permissions;
  }

  public RecipeComposition composition(int recipeId, UserRole role) {
    // Composition includes both costs and ingredients, so neither existing grant alone is enough.
    permissions.require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
    var value = query.composition(recipeId).orElse(null);
    if (value == null) return null;
    var d = value.definition();
    return new RecipeComposition(
        new RecipeDefinition(d.header(), d.stocks(), d.usages(), d.observedAt()),
        value.folderPath(),
        value.outlets(),
        value.sourceComponentTotal(),
        value.calculatedTotal(),
        value.difference(),
        value.issues(),
        value.stale());
  }

  public RecipeDetails details(int recipeId, UserRole role) {
    permissions.require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    permissions.require(role, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
    var value = query.details(recipeId).orElse(null);
    if (value == null) return null;
    var health =
        connectorHealth.health().stream()
            .filter(c -> c.source().equals("CTB") && c.connector().equals("ctb-revenue"))
            .findFirst()
            .orElse(null);
    value =
        new RecipeDetails(
            value.recipeId(),
            value.name(),
            value.costSelection(),
            value.sections(),
            value.latestAttempts(),
            value.usedIn(),
            value.uses(),
            value.compositionSources(),
            health,
            collectionEnabled,
            value.freshnessLimitSeconds());
    try {
      permissions.require(role, new ResourceKey("connectors"), PermissionAction.READ);
      return value;
    } catch (AccessDeniedException denied) {
      return new RecipeDetails(
          value.recipeId(),
          value.name(),
          value.costSelection(),
          value.sections().stream().map(this::redact).toList(),
          value.latestAttempts().stream().map(this::redact).toList(),
          value.usedIn(),
          value.uses(),
          value.compositionSources().stream().map(this::redact).toList(),
          value.collection(),
          value.collectionEnabled(),
          value.freshnessLimitSeconds());
    }
  }

  private RecipeEvidence redact(RecipeEvidence e) {
    return new RecipeEvidence(
        null, e.sourceSystem(), e.fetcher(), e.endpoint(), null, e.capturedAt(), null, null);
  }

  private RecipeSection redact(RecipeSection s) {
    return new RecipeSection(
        s.recipeId(), s.section(), s.successful(), s.notice(), s.fields(), redact(s.evidence()));
  }

  public RecipeCatalog catalog(UserRole role) {
    permissions.require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    return query.catalog().orElse(null);
  }
}
