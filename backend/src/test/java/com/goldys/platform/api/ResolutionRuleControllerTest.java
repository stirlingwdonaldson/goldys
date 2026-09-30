package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.reconciliation.ResolutionRuleService;
import com.goldys.platform.reconciliation.ResolutionRuleView;
import com.goldys.platform.reconciliation.RuleAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ResolutionRuleController.class)
@Import(SecurityConfig.class)
class ResolutionRuleControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ResolutionRuleService rules;
  @MockitoBean RuleAuditService audit;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void listsCurrentRules() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(rules.list())
        .thenReturn(
            List.of(
                new ResolutionRuleView(
                    UUID.randomUUID(),
                    "daily_sales",
                    "daily_sales",
                    "priority",
                    List.of("CTB"),
                    null,
                    Instant.now(),
                    "a@b.com")));

    mvc.perform(get("/api/reconciliation/rules").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].entityType").value("daily_sales"))
        .andExpect(jsonPath("$[0].strategy").value("priority"))
        .andExpect(jsonPath("$[0].sourcePriority[0]").value("CTB"));
  }

  @Test
  void createsARule() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/reconciliation/rules")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityType\":\"daily_sales\",\"fieldKey\":\"daily_sales\",\"strategy\":\"priority\",\"sourcePriority\":[\"CTB\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  void recomputeStatusIsComplete() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(get("/api/reconciliation/recompute/status").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("complete"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
