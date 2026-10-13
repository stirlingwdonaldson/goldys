package com.goldys.platform.api;

import com.goldys.platform.application.SalesReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.DataPage;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  DataPage<SalesReportingService.DailySalesRow> daily(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return salesReporting.dailySales(currentUser.roleOf(user), from, to, page, size);
  }

  @GetMapping("/latest")
  SalesReportingService.LatestSales latest(@AuthenticationPrincipal AccountUserDetails user) {
    return salesReporting.latestTradingDay(currentUser.roleOf(user));
  }
}
