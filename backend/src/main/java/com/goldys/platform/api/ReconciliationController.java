package com.goldys.platform.api;

import com.goldys.platform.application.ReconciliationApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DataPage;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reconciliation exceptions, per-date/per-product drill-in, and the manual override action. */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
  private final ReconciliationApplicationService reconciliation;
  private final CurrentUserService currentUser;

  public ReconciliationController(
      ReconciliationApplicationService reconciliation, CurrentUserService currentUser) {
    this.reconciliation = reconciliation;
    this.currentUser = currentUser;
  }

  @GetMapping("/exceptions")
  DataPage<ReconciliationApplicationService.DailyException> exceptions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.listDailyExceptions(currentUser.roleOf(user), page, size);
  }

  @GetMapping("/records/{date}")
  ReconciliationApplicationService.Record record(
      @PathVariable LocalDate date, @AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.dailyRecord(currentUser.roleOf(user), date);
  }

  @PostMapping("/records/{date}/override")
  ReconciliationApplicationService.OverrideResult override(
      @PathVariable LocalDate date,
      @RequestBody OverrideRequestDto body,
      @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return reconciliation.overrideDaily(role, user.email(), date, body.source(), body.reason());
  }

  @GetMapping("/products/exceptions")
  DataPage<ReconciliationApplicationService.ProductException> productExceptions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.listProductExceptions(currentUser.roleOf(user), page, size);
  }

  @GetMapping("/products")
  List<String> products(@AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.listProducts(currentUser.roleOf(user));
  }

  @GetMapping("/audit")
  DataPage<ReconciliationApplicationService.AuditEntry> audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.audit(currentUser.roleOf(user), page, size);
  }

  @GetMapping("/products/{date}/{product}")
  ReconciliationApplicationService.ProductRecord productRecord(
      @PathVariable LocalDate date,
      @PathVariable String product,
      @AuthenticationPrincipal AccountUserDetails user) {
    return reconciliation.productRecord(currentUser.roleOf(user), date, product);
  }

  @PostMapping("/products/{date}/{product}/override")
  ReconciliationApplicationService.OverrideResult productOverride(
      @PathVariable LocalDate date,
      @PathVariable String product,
      @RequestBody OverrideRequestDto body,
      @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return reconciliation.overrideProduct(
        role, user.email(), date, product, body.source(), body.reason());
  }

  record OverrideRequestDto(String source, String reason) {}
}
