package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.reconciliation.DailySalesConflict;
import com.goldys.platform.reconciliation.DailySalesOverrideService;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.SourceTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ReconciliationController.class)
@Import(SecurityConfig.class)
class ReconciliationControllerTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired MockMvc mvc;

  @MockitoBean DailySalesReconciliationService reconciliation;
  @MockitoBean DailySalesOverrideService overrides;
  @MockitoBean CanonicalDailySalesQuery dailySales;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void exceptionsReturnsTheConflict() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(OWNER);
    when(reconciliation.conflicts())
        .thenReturn(
            List.of(
                new DailySalesConflict(
                    LocalDate.of(2026, 9, 13),
                    List.of(
                        new SourceTotal("LIGHTSPEED", new BigDecimal("27650.66")),
                        new SourceTotal("CTB", new BigDecimal("20990.83"))),
                    "conflict")));

    mvc.perform(get("/api/reconciliation/exceptions").with(oidcLogin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].recordId").value("2026-09-13"))
        .andExpect(jsonPath("$[0].status").value("conflict"))
        .andExpect(jsonPath("$[0].sources[0].source").value("LIGHTSPEED"));
  }

  @Test
  void readDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(OWNER);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(permissions)
        .require(any(), any(), any());

    mvc.perform(get("/api/reconciliation/exceptions").with(oidcLogin()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void overrideDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(OWNER);
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(overrides)
        .save(any(), any(), any(), any(), any(), any());

    mvc.perform(
            post("/api/reconciliation/records/2026-09-13/override")
                .with(oidc())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"LIGHTSPEED\",\"reason\":\"typo\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void overrideReturnsOk() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(OWNER);

    mvc.perform(
            post("/api/reconciliation/records/2026-09-13/override")
                .with(oidc())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"LIGHTSPEED\",\"reason\":\"typo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.recordId").value("2026-09-13"));
  }

  private static RequestPostProcessor oidc() {
    return oidcLogin().idToken(token -> token.issuer("http://localhost/issuer"));
  }
}
