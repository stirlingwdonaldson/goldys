package com.goldys.platform.api;

import com.goldys.platform.application.ReservationReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationSummary;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resolved reservation metrics for the Reservations screen. Delivery-only. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
  private final ReservationReportingService reporting;
  private final CurrentUserService currentUser;

  public ReservationController(
      ReservationReportingService reporting, CurrentUserService currentUser) {
    this.reporting = reporting;
    this.currentUser = currentUser;
  }

  @GetMapping("/summary")
  ResponseEntity<ReservationSummary> summary(
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting
        .summary(currentUser.roleOf(user), date)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/covers")
  List<CoversMetric> covers(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reporting.dailyCovers(currentUser.roleOf(user), from, to);
  }
}
