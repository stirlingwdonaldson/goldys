package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.ReconciliationApplicationService;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
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

@WebMvcTest(ReconciliationController.class)
@Import(SecurityConfig.class)
class ReconciliationControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ReconciliationApplicationService reconciliation;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void exceptionsReturnsTheConflict() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.listDailyExceptions(any()))
        .thenReturn(
            List.of(
                new ReconciliationApplicationService.DailyException(
                    "2026-09-13:daily_sales",
                    "2026-09-13",
                    "2026-09-13",
                    "daily_sales",
                    List.of(
                        new ReconciliationApplicationService.SourceValue("LIGHTSPEED", "27650.66"),
                        new ReconciliationApplicationService.SourceValue("CTB", "20990.83")),
                    "conflict")));

    mvc.perform(get("/api/reconciliation/exceptions").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].recordId").value("2026-09-13"))
        .andExpect(jsonPath("$[0].status").value("conflict"))
        .andExpect(jsonPath("$[0].sources[0].source").value("LIGHTSPEED"));
  }

  @Test
  void readDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(reconciliation)
        .listDailyExceptions(any());

    mvc.perform(get("/api/reconciliation/exceptions").with(authenticated(owner())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void overrideDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(reconciliation)
        .overrideDaily(any(), any(), any(), any(), any());

    mvc.perform(
            post("/api/reconciliation/records/2026-09-13/override")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"LIGHTSPEED\",\"reason\":\"typo\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void overrideReturnsOk() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.overrideDaily(any(), any(), any(), any(), any()))
        .thenReturn(
            new ReconciliationApplicationService.OverrideResult(true, "2026-09-13", "daily_sales"));

    mvc.perform(
            post("/api/reconciliation/records/2026-09-13/override")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"LIGHTSPEED\",\"reason\":\"typo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.recordId").value("2026-09-13"));
  }

  @Test
  void productExceptionsReturnsTheConflict() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.listProductExceptions(any()))
        .thenReturn(
            List.of(
                new ReconciliationApplicationService.ProductException(
                    "2026-09-14:garlic aioli",
                    "garlic aioli",
                    "garlic aioli",
                    "garlic aioli",
                    List.of(
                        new ReconciliationApplicationService.SourceValue(
                            "LIGHTSPEED", "150 × $380.88"),
                        new ReconciliationApplicationService.SourceValue("CTB", "127 × $322.46")),
                    "conflict")));

    mvc.perform(get("/api/reconciliation/products/exceptions").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].field").value("garlic aioli"))
        .andExpect(jsonPath("$[0].status").value("conflict"))
        .andExpect(jsonPath("$[0].sources[0].source").value("LIGHTSPEED"))
        .andExpect(jsonPath("$[0].sources[0].value").value("150 × $380.88"));
  }

  @Test
  void productsReturnsDistinctNames() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.listProducts(any()))
        .thenReturn(List.of("garlic aioli", "pint carlton draught"));

    mvc.perform(get("/api/reconciliation/products").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("garlic aioli"))
        .andExpect(jsonPath("$[1]").value("pint carlton draught"));
  }

  @Test
  void auditReturnsRuleAndOverrideHistory() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.audit(any()))
        .thenReturn(
            List.of(
                new ReconciliationApplicationService.AuditEntry(
                    "rule",
                    "created",
                    "daily_sales",
                    "daily_sales",
                    null,
                    null,
                    "a@b.com",
                    java.time.Instant.parse("2026-09-13T09:00:00Z")),
                new ReconciliationApplicationService.AuditEntry(
                    "override",
                    "set",
                    "daily_sales",
                    "daily_sales",
                    "LIGHTSPEED",
                    "typo",
                    "a@b.com",
                    java.time.Instant.parse("2026-09-14T09:00:00Z"))));

    mvc.perform(get("/api/reconciliation/audit").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kind").value("rule"))
        .andExpect(jsonPath("$[0].change").value("created"))
        .andExpect(jsonPath("$[1].kind").value("override"))
        .andExpect(jsonPath("$[1].source").value("LIGHTSPEED"));
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of());
    return authentication(auth);
  }
}
