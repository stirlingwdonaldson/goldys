package com.goldys.platform.api;

import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard summary: the open-conflict count is the only live metric for the first slice. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final DailySalesReconciliationService reconciliation;

  public DashboardController(DailySalesReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @GetMapping("/summary")
  SummaryDto summary() {
    return new SummaryDto(null, reconciliation.conflicts().size(), null, null);
  }

  record SummaryDto(
      Integer ingestionCompleteness,
      Integer openConflicts,
      String timeToDetectFailure,
      OverrideUsageDto overrideUsage) {}

  record OverrideUsageDto(Integer count, String period) {}
}
