package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.reconciliation.ResolvedDailySalesQuery;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Daily sales for the Sales screen: one row per (date, source), newest first. */
@RestController
@RequestMapping("/api/sales")
public class SalesController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final CanonicalDailySalesQuery dailySales;
  private final ResolvedDailySalesQuery resolvedDailySales;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public SalesController(
      CanonicalDailySalesQuery dailySales,
      ResolvedDailySalesQuery resolvedDailySales,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.dailySales = dailySales;
    this.resolvedDailySales = resolvedDailySales;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/daily")
  List<DailySalesDto> daily(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return dailySales.currentDailySales().stream()
        .sorted(Comparator.comparing(DailySalesView::tradingDate).reversed())
        .map(SalesController::toDto)
        .toList();
  }

  /**
   * The latest trading date's resolved total, or nulls when there is no data yet or the latest date
   * is still unresolved. Dashboards consume this instead of summing per-source rows, which would
   * double-count sources describing the same revenue.
   */
  @GetMapping("/latest")
  LatestSalesDto latest(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return resolvedDailySales
        .latest()
        .map(
            v ->
                new LatestSalesDto(
                    v.tradingDate().toString(), v.totalSales(), v.authoritativeSource()))
        .orElseGet(() -> new LatestSalesDto(null, null, null));
  }

  private static DailySalesDto toDto(DailySalesView v) {
    return new DailySalesDto(
        v.tradingDate().toString(), v.sourceSystem(), v.totalSales(), v.gstTotal(), v.netTotal());
  }

  record DailySalesDto(
      String date, String source, BigDecimal totalSales, BigDecimal gst, BigDecimal net) {}

  record LatestSalesDto(String date, BigDecimal total, String authoritativeSource) {}
}
