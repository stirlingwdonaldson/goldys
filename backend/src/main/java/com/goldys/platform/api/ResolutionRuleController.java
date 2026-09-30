package com.goldys.platform.api;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.reconciliation.ResolutionRuleView;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD + audit for standing resolution rules, and the (derived) recompute status. */
@RestController
@RequestMapping("/api/reconciliation")
public class ResolutionRuleController {
  private static final ResourceKey RESOURCE = new ResourceKey("reconciliation.sales");

  private final ResolutionRuleService rules;
  private final RuleAuditService audit;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ResolutionRuleController(
      ResolutionRuleService rules,
      RuleAuditService audit,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.rules = rules;
    this.audit = audit;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping("/rules")
  List<ResolutionRuleView> list(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return rules.list();
  }

  @PostMapping("/rules")
  ResolutionRuleView save(
      @RequestBody RuleRequest body, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return rules.save(
        role,
        user.email(),
        new ResolutionRuleService.RuleInput(
            body.entityType(),
            body.fieldKey(),
            body.strategy(),
            body.customLogic(),
            body.sourcePriority()));
  }

  @DeleteMapping("/rules/{id}")
  void delete(@PathVariable String id, @AuthenticationPrincipal AccountUserDetails user) {
    rules.delete(currentUser.roleOf(user), user.email(), id);
  }

  @GetMapping("/rules/audit")
  List<RuleAuditService.RuleAuditEntry> audit(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return audit.history();
  }

  @GetMapping("/recompute/status")
  RecomputeStatusDto recomputeStatus(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return new RecomputeStatusDto("complete", rules.lastChangedAt().orElse(null));
  }

  record RecomputeStatusDto(String state, Instant lastChangedAt) {}

  record RuleRequest(
      String entityType,
      String fieldKey,
      String strategy,
      String customLogic,
      List<String> sourcePriority) {}
}
