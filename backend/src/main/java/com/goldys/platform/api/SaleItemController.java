package com.goldys.platform.api;

import com.goldys.platform.application.SaleItemReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.SaleItemMix;
import com.goldys.platform.semantic.SaleItemRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resolved sale-item metrics. Delivery-only. */
@RestController
@RequestMapping("/api/sale-items")
public class SaleItemController {
  private final SaleItemReportingService reporting;
  private final CurrentUserService currentUser;

  public SaleItemController(SaleItemReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/mix")
  List<SaleItemMix> mix(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.dailyByCategory(currentUser.roleOf(user), from, to);
  }

  @GetMapping
  DataPage<SaleItemRow> list(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String saleNumber,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.list(currentUser.roleOf(user), category, saleNumber, from, to, page, size);
  }
}
