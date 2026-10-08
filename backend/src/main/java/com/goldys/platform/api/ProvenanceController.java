package com.goldys.platform.api;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.Provenance;
import com.goldys.platform.semantic.TrustQuery;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full provenance drill-down for a metric's resolved value on one date. The compact trust summary
 * is already gated by the metric's permission; the raw per-source and resolution detail exposed
 * here is gated on the metric's domain read, enforced before any provenance is assembled. Raw
 * ingestion record references are additionally gated on the {@code connectors} read.
 */
@RestController
@RequestMapping("/api/provenance")
public class ProvenanceController {
  private static final ResourceKey CONNECTORS = new ResourceKey("connectors");

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
    UserRole role = currentUser.roleOf(user);
    permissions.require(role, new ResourceKey(permission), PermissionAction.READ);
    Provenance provenance = trustQuery.provenanceFor(metric, date);
    return mayReadRawRecords(role) ? provenance : withoutRawRecords(provenance);
  }

  /** Raw-record references are gated on {@code connectors} READ (spec §8). */
  private boolean mayReadRawRecords(UserRole role) {
    try {
      permissions.require(role, CONNECTORS, PermissionAction.READ);
      return true;
    } catch (AccessDeniedException denied) {
      return false;
    }
  }

  private static Provenance withoutRawRecords(Provenance provenance) {
    return new Provenance(
        provenance.metric(),
        provenance.date(),
        provenance.resolvedValue(),
        provenance.trust(),
        provenance.sources(),
        provenance.resolution(),
        List.of());
  }
}
