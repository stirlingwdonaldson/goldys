package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Recipes screen's read model. Authorizes the read and delegates to the resolved recipe
 * semantic query; margin, gross profit and cost gap come only from the resolved projection.
 */
@Service
public class RecipeReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("recipes.cost");

  private final RecipeMetricsQuery metrics;
  private final PermissionService permissions;

  public RecipeReportingService(RecipeMetricsQuery metrics, PermissionService permissions) {
    this.metrics = metrics;
    this.permissions = permissions;
  }

  /** The resolved per-dish costing, authorized on {@code recipes.cost}. */
  public List<RecipeCosting> costing(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.costing();
  }
}
