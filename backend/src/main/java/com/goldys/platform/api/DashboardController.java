package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.ingestion.IngestionActivityPoint;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.reconciliation.ProductSalesExceptionQuery;
import com.goldys.platform.reconciliation.ResolvedDailySalesQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary: ingestion health, open conflicts, and manual-override usage. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ResolvedDailySalesQuery resolvedDailySales;
  private final ProductSalesExceptionQuery productSalesExceptions;
  private final CanonicalProductSalesQuery productSales;
  private final IngestionService ingestion;
  private final OverrideUsageService overrideUsage;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public DashboardController(
      ResolvedDailySalesQuery resolvedDailySales,
      ProductSalesExceptionQuery productSalesExceptions,
      CanonicalProductSalesQuery productSales,
      IngestionService ingestion,
      OverrideUsageService overrideUsage,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.resolvedDailySales = resolvedDailySales;
    this.productSalesExceptions = productSalesExceptions;
    this.productSales = productSales;
    this.ingestion = ingestion;
    this.overrideUsage = overrideUsage;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/summary")
  SummaryDto summary(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    int openConflicts =
        (int) (resolvedDailySales.countOpenConflicts() + productSalesExceptions.countOpen());
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

  @GetMapping("/activity")
  List<ActivityDto> activity(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return ingestion.activity(14).stream().map(DashboardController::toActivityDto).toList();
  }

  @GetMapping("/top-sellers")
  List<TopSellerDto> topSellers(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return productSales.topProductsByAmount(LocalDate.now().minusDays(30), 5).stream()
        .map(p -> new TopSellerDto(p.productNameKey(), p.quantitySold(), p.amount()))
        .toList();
  }

  @GetMapping("/sales-trend")
  List<SalesTrendDto> salesTrend(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return resolvedDailySales.between(LocalDate.now().minusDays(13), LocalDate.now()).stream()
        .map(v -> new SalesTrendDto(v.tradingDate().toString(), v.totalSales()))
        .toList();
  }

  private static ActivityDto toActivityDto(IngestionActivityPoint point) {
    return new ActivityDto(point.date(), point.clean(), point.failed());
  }

  record ActivityDto(String date, int clean, int failed) {}

  record TopSellerDto(String name, BigDecimal quantitySold, BigDecimal amount) {}

  record SalesTrendDto(String date, BigDecimal total) {}
}
