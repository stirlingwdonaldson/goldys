package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.reconciliation.ProductSalesReconciliationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary: ingestion health, open conflicts, and manual-override usage. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final DailySalesReconciliationService reconciliation;
  private final ProductSalesReconciliationService productSales;
  private final IngestionService ingestion;
  private final OverrideUsageService overrideUsage;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public DashboardController(
      DailySalesReconciliationService reconciliation,
      ProductSalesReconciliationService productSales,
      IngestionService ingestion,
      OverrideUsageService overrideUsage,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.reconciliation = reconciliation;
    this.productSales = productSales;
    this.ingestion = ingestion;
    this.overrideUsage = overrideUsage;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/summary")
  SummaryDto summary(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    int openConflicts = reconciliation.conflicts().size() + productSales.conflicts().size();
    IngestionHealth health = ingestion.health();
    OverrideUsage usage = overrideUsage.usage();
    return new SummaryDto(
        health.completenessPercent(),
        openConflicts,
        health.timeToDetectFailure(),
        new OverrideUsageDto(usage.count(), usage.period()));
  }

  record SummaryDto(
      Integer ingestionCompleteness,
      Integer openConflicts,
      String timeToDetectFailure,
      OverrideUsageDto overrideUsage) {}

  record OverrideUsageDto(Integer count, String period) {}
}
