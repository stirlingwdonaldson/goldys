package com.goldys.platform.api;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.StaffProfileService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.reconciliation.DailySalesConflict;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reconciliation exceptions, per-date drill-in, and the manual override action. */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
  private final DailySalesReconciliationService reconciliation;
  private final DailySalesOverrideService overrides;
  private final CanonicalDailySalesQuery dailySales;
  private final StaffProfileService profiles;

  public ReconciliationController(
      DailySalesReconciliationService reconciliation,
      DailySalesOverrideService overrides,
      CanonicalDailySalesQuery dailySales,
      StaffProfileService profiles) {
    this.reconciliation = reconciliation;
    this.overrides = overrides;
    this.dailySales = dailySales;
    this.profiles = profiles;
  }

  @GetMapping("/exceptions")
  List<ExceptionDto> exceptions() {
    return reconciliation.conflicts().stream().map(this::toException).toList();
  }

  @GetMapping("/records/{date}")
  RecordDto record(@PathVariable LocalDate date) {
    List<SourceValueDto> sources =
        dailySales.currentDailySalesForDate(date).stream()
            .map(s -> new SourceValueDto(s.sourceSystem(), plain(s.totalSales())))
            .toList();
    Optional<String> authoritative = overrides.currentAuthoritativeSource(date);
    FieldDto field =
        new FieldDto(
            "daily_sales",
            "Daily sales",
            sources,
            authoritative.isPresent(),
            authoritative.orElse(null));
    return new RecordDto(date.toString(), date.toString(), "day", List.of(field));
  }

  @PostMapping("/records/{date}/override")
  OverrideResultDto override(
      @PathVariable LocalDate date,
      @RequestBody OverrideRequestDto body,
      @AuthenticationPrincipal OidcUser user) {
    UserRole role = roleFor(user);
    overrides.save(
        role, user.getIssuer().toString(), user.getSubject(), date, body.source(), body.reason());
    return new OverrideResultDto(true, date.toString(), "daily_sales");
  }

  private ExceptionDto toException(DailySalesConflict conflict) {
    List<SourceValueDto> sources =
        conflict.sources().stream()
            .map(s -> new SourceValueDto(s.sourceSystem(), plain(s.totalSales())))
            .toList();
    return new ExceptionDto(
        conflict.tradingDate() + ":daily_sales",
        conflict.tradingDate().toString(),
        conflict.tradingDate().toString(),
        "daily_sales",
        sources,
        conflict.status());
  }

  private UserRole roleFor(OidcUser user) {
    StaffProfileService.StaffProfileSummary summary =
        profiles
            .findActive(user.getIssuer().toString(), user.getSubject())
            .orElseThrow(
                () ->
                    new com.goldys.platform.auth.AccessDeniedException("No active staff profile"));
    return new UserRole(
        new DepartmentCode(summary.department()), new SeniorityCode(summary.seniority()));
  }

  private static String plain(java.math.BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  record ExceptionDto(
      String id,
      String recordId,
      String entity,
      String field,
      List<SourceValueDto> sources,
      String status) {}

  record RecordDto(String id, String entity, String entityType, List<FieldDto> fields) {}

  record FieldDto(
      String name,
      String label,
      List<SourceValueDto> sources,
      boolean overridden,
      String authoritativeSource) {}

  record SourceValueDto(String source, String value) {}

  record OverrideRequestDto(String source, String reason) {}

  record OverrideResultDto(boolean ok, String recordId, String field) {}
}
