package com.goldys.platform.api;

import com.goldys.platform.application.InventoryReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resolved inventory metrics for the Inventory screen. Delivery-only. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryReportingService reporting;
  private final CurrentUserService currentUser;

  public InventoryController(
      InventoryReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/summary")
  InventoryReportingService.InventorySummary summary(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.summary(currentUser.roleOf(user), from, to);
  }
}
