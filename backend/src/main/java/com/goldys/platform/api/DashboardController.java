package com.goldys.platform.api;

import com.goldys.platform.application.DashboardApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary and widgets. Delivery-only: delegates to the dashboard application service. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final DashboardApplicationService dashboard;
  private final CurrentUserService currentUser;

  public DashboardController(
      DashboardApplicationService dashboard, CurrentUserService currentUser) {
    this.dashboard = dashboard;
    this.currentUser = currentUser;
  }

  @GetMapping("/summary")
  DashboardApplicationService.Summary summary(@AuthenticationPrincipal AccountUserDetails user) {
    return dashboard.summary(currentUser.roleOf(user));
  }

  /** The dashboard's whole initial render, in one request. */
  @GetMapping("/bootstrap")
  DashboardApplicationService.Bootstrap bootstrap(
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboard.bootstrap(currentUser.roleOf(user));
  }

  @GetMapping("/activity")
  List<DashboardApplicationService.ActivityPoint> activity(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboard.activity(currentUser.roleOf(user), from, to);
  }

  @GetMapping("/top-sellers")
  List<DashboardApplicationService.TopSeller> topSellers(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(value = "limit", required = false) Integer limit,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboard.topSellers(currentUser.roleOf(user), from, to, limit);
  }

  @GetMapping("/sales-trend")
  List<DashboardApplicationService.SalesTrend> salesTrend(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return dashboard.salesTrend(currentUser.roleOf(user), from, to);
  }
}
