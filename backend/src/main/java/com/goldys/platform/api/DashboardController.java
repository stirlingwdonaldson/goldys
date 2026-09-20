package com.goldys.platform.api;

import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary: the open-conflict count is the only live metric for the first slice. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final DailySalesReconciliationService reconciliation;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public DashboardController(
      DailySalesReconciliationService reconciliation,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.reconciliation = reconciliation;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/summary")
  SummaryDto summary(@AuthenticationPrincipal OidcUser user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return new SummaryDto(null, reconciliation.conflicts().size(), null, null);
  }

  record SummaryDto(
      Integer ingestionCompleteness,
      Integer openConflicts,
      String timeToDetectFailure,
      OverrideUsageDto overrideUsage) {}

  record OverrideUsageDto(Integer count, String period) {}
}
