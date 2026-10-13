package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic catalogue-driven metric query over the semantic layer.
 *
 * <p>This is the flexible REST counterpart to the fixed reporting tools and the saved-dashboard
 * render path: it accepts the same bounded {@link MetricQuery} shape (metric + range + grain +
 * dimensions + comparison) and returns the raw {@link MetricResult}. The query is validated against
 * the catalogue by {@link MetricQueryService} (no freeform column/SQL/filter surface), and the
 * metric's own {@code requiredPermission} gates the read — the same per-metric authorization the
 * saved-dashboard render path applies.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricQueryController {
  private final MetricQueryService queries;
  private final MetricCatalog catalog;
  private final PermissionService permissions;
  private final CurrentUserService currentUser;

  public MetricQueryController(
      MetricQueryService queries,
      MetricCatalog catalog,
      PermissionService permissions,
      CurrentUserService currentUser) {
    this.queries = queries;
    this.catalog = catalog;
    this.permissions = permissions;
    this.currentUser = currentUser;
  }

  @PostMapping("/query")
  MetricResult query(
      @RequestBody MetricQuery body, @AuthenticationPrincipal AccountUserDetails user) {
    String requiredPermission = catalog.definition(body.metric()).requiredPermission();
    UserRole role = currentUser.roleOf(user);
    permissions.require(role, new ResourceKey(requiredPermission), PermissionAction.READ);
    return queries.query(body);
  }
}
