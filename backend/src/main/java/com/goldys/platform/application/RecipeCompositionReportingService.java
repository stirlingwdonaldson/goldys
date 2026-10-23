package com.goldys.platform.application;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.*;
import org.springframework.stereotype.Service;

@Service
public class RecipeCompositionReportingService {
  private final RecipeCompositionQuery query;
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
    return query.composition(recipeId).orElse(null);
  }

  public RecipeCatalog catalog(UserRole role) {
    permissions.require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    return query.catalog().orElse(null);
  }
}
