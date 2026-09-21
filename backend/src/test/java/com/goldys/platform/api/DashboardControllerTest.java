package com.goldys.platform.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.ingestion.IngestionHealth;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.OverrideUsage;
import com.goldys.platform.reconciliation.OverrideUsageService;
import com.goldys.platform.reconciliation.ProductSalesReconciliationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean DailySalesReconciliationService reconciliation;
  @MockitoBean ProductSalesReconciliationService productSales;
  @MockitoBean IngestionService ingestion;
  @MockitoBean OverrideUsageService overrideUsage;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void summaryPopulatesIngestionMetrics() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.conflicts()).thenReturn(List.of());
    when(productSales.conflicts()).thenReturn(List.of());
    when(ingestion.health()).thenReturn(new IngestionHealth(92, "42m avg"));
    when(overrideUsage.usage()).thenReturn(new OverrideUsage(3, "last 7 days"));

    mvc.perform(get("/api/dashboard/summary").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ingestionCompleteness").value(92))
        .andExpect(jsonPath("$.openConflicts").value(0))
        .andExpect(jsonPath("$.timeToDetectFailure").value("42m avg"))
        .andExpect(jsonPath("$.overrideUsage.count").value(3))
        .andExpect(jsonPath("$.overrideUsage.period").value("last 7 days"));
  }

  @Test
  void summaryReturnsNullMetricsWhenLedgerIsEmpty() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reconciliation.conflicts()).thenReturn(List.of());
    when(productSales.conflicts()).thenReturn(List.of());
    when(ingestion.health()).thenReturn(new IngestionHealth(null, null));
    when(overrideUsage.usage()).thenReturn(new OverrideUsage(0, "last 7 days"));

    mvc.perform(get("/api/dashboard/summary").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ingestionCompleteness").value(nullValue()))
        .andExpect(jsonPath("$.timeToDetectFailure").value(nullValue()))
        .andExpect(jsonPath("$.overrideUsage.count").value(0));
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
