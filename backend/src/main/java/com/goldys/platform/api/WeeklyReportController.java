package com.goldys.platform.api;

import com.goldys.platform.application.WeeklyReport;
import com.goldys.platform.application.WeeklyReportService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/weekly")
public class WeeklyReportController {
  private final WeeklyReportService reports;
  private final CurrentUserService currentUser;

  public WeeklyReportController(WeeklyReportService reports, CurrentUserService currentUser) {
    this.reports = reports;
    this.currentUser = currentUser;
  }

  @GetMapping
  WeeklyReport report(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(defaultValue = "4") int weeks,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reports.report(currentUser.roleOf(user), from, weeks);
  }
}
