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
  private final com.goldys.platform.application.WeeklyReportTargets targets;

  public WeeklyReportController(
      WeeklyReportService reports,
      CurrentUserService currentUser,
      com.goldys.platform.application.WeeklyReportTargets targets) {
    this.reports = reports;
    this.currentUser = currentUser;
    this.targets = targets;
  }

  @GetMapping("/targets")
  java.util.List<com.goldys.platform.application.WeeklyReportTargets.Target> targets(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
      @AuthenticationPrincipal AccountUserDetails user) {
    return targets.get(currentUser.roleOf(user), week);
  }

  @PutMapping("/targets")
  java.util.List<com.goldys.platform.application.WeeklyReportTargets.Target> saveTargets(
      @RequestBody com.goldys.platform.application.WeeklyReportTargets.Edit edit,
      @AuthenticationPrincipal AccountUserDetails user) {
    return targets.save(currentUser.roleOf(user), user.getUsername(), edit);
  }

  @GetMapping
  WeeklyReport report(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(defaultValue = "4") int weeks,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reports.report(currentUser.roleOf(user), from, weeks);
  }
}
