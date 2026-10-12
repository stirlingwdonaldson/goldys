package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Recipes screen's ingredient read model. Authorizes the read and delegates to the resolved
 * recipe-ingredient semantic query; no request-time access to canonical source rows.
 */
@Service
public class RecipeIngredientReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("recipes.ingredients");

  private final RecipeIngredientMetricsQuery metrics;
  private final PermissionService permissions;

  public RecipeIngredientReportingService(
      RecipeIngredientMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** One recipe's resolved ingredient lines, authorized on {@code recipes.ingredients}. */
  public List<RecipeIngredient> ingredients(UserRole role, int recipeId) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.ingredientsFor(recipeId);
  }
}
