package com.goldys.platform.api;

import com.goldys.platform.application.SalesReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Daily sales for the Sales screen. Delivery-only: delegates to the sales reporting service. */
@RestController
@RequestMapping("/api/sales")
public class SalesController {
  private final SalesReportingService salesReporting;
  private final CurrentUserService currentUser;

  public SalesController(SalesReportingService salesReporting, CurrentUserService currentUser) {
    this.salesReporting = salesReporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/daily")
  List<SalesReportingService.DailySalesRow> daily(
      @AuthenticationPrincipal AccountUserDetails user) {
    return salesReporting.dailySales(currentUser.roleOf(user));
  }

  @GetMapping("/latest")
  SalesReportingService.LatestSales latest(@AuthenticationPrincipal AccountUserDetails user) {
    return salesReporting.latestTradingDay(currentUser.roleOf(user));
  }
}
