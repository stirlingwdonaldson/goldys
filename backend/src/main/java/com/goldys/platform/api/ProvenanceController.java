package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full provenance drill-down for a metric's resolved value on one date. The compact trust summary
 * is already gated by the metric's permission; the raw per-source and resolution detail exposed
 * here is gated on the metric's domain read, enforced before any provenance is assembled.
 */
@RestController
@RequestMapping("/api/provenance")
public class ProvenanceController {
  private final TrustQuery trustQuery;
  private final MetricCatalog catalog;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ProvenanceController(
      TrustQuery trustQuery,
      MetricCatalog catalog,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.trustQuery = trustQuery;
    this.catalog = catalog;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/{metricId}/{date}")
  Provenance provenance(
      @PathVariable String metricId,
      @PathVariable LocalDate date,
      @AuthenticationPrincipal AccountUserDetails user) {
    MetricId metric = MetricId.fromValue(metricId);
    String permission = catalog.definition(metric).requiredPermission();
    permissions.require(
        currentUser.roleOf(user), new ResourceKey(permission), PermissionAction.READ);
    return trustQuery.provenanceFor(metric, date);
  }
}
