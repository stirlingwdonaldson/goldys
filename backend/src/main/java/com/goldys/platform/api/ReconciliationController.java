package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.reconciliation.DailySalesConflict;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final DailySalesReconciliationService reconciliation;
  private final DailySalesOverrideService overrides;
  private final CanonicalDailySalesQuery dailySales;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ReconciliationController(
      DailySalesReconciliationService reconciliation,
      DailySalesOverrideService overrides,
      CanonicalDailySalesQuery dailySales,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.reconciliation = reconciliation;
    this.overrides = overrides;
    this.dailySales = dailySales;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/exceptions")
  List<ExceptionDto> exceptions(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return reconciliation.conflicts().stream().map(this::toException).toList();
  }

  @GetMapping("/records/{date}")
  RecordDto record(@PathVariable LocalDate date, @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
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
      @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    overrides.save(role, user.email(), date, body.source(), body.reason());
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
