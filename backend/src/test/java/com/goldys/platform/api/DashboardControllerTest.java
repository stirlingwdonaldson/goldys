package com.goldys.platform.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.DashboardApplicationService;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.reconciliation.OverrideUsage;
import java.math.BigDecimal;
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

  @MockitoBean DashboardApplicationService dashboard;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void summaryPopulatesIngestionMetrics() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboard.summary(any()))
        .thenReturn(
            new DashboardApplicationService.Summary(
                92, 0, "42m avg", new OverrideUsage(3, "last 7 days")));

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
    when(dashboard.summary(any()))
        .thenReturn(
            new DashboardApplicationService.Summary(
                null, 0, null, new OverrideUsage(0, "last 7 days")));

    mvc.perform(get("/api/dashboard/summary").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ingestionCompleteness").value(nullValue()))
        .andExpect(jsonPath("$.timeToDetectFailure").value(nullValue()))
        .andExpect(jsonPath("$.overrideUsage.count").value(0));
  }

  @Test
  void activityReturnsDailySeries() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboard.activity(any()))
        .thenReturn(
            List.of(
                new DashboardApplicationService.ActivityPoint("2026-09-21", 5, 0),
                new DashboardApplicationService.ActivityPoint("2026-09-22", 4, 1)));

    mvc.perform(get("/api/dashboard/activity").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-09-21"))
        .andExpect(jsonPath("$[0].clean").value(5))
        .andExpect(jsonPath("$[0].failed").value(0))
        .andExpect(jsonPath("$[1].date").value("2026-09-22"))
        .andExpect(jsonPath("$[1].failed").value(1));
  }

  @Test
  void activityDeniedReturnsForbidden() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(AccessDeniedException.forResource("reconciliation.sales"))
        .when(dashboard)
        .activity(any());

    mvc.perform(get("/api/dashboard/activity").with(authenticated(owner())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_PERMITTED"));
  }

  @Test
  void topSellersDelegatesToApplicationService() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboard.topSellers(any()))
        .thenReturn(
            List.of(
                new DashboardApplicationService.TopSeller(
                    "garlic aioli", new BigDecimal("150"), new BigDecimal("380.88"), true)));

    mvc.perform(get("/api/dashboard/top-sellers").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("garlic aioli"))
        .andExpect(jsonPath("$[0].quantitySold").value(150))
        .andExpect(jsonPath("$[0].amount").value(380.88))
        .andExpect(jsonPath("$[0].hasConflict").value(true));
  }

  @Test
  void bootstrapReturnsTheCombinedInitialRender() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dashboard.bootstrap(any()))
        .thenReturn(
            new DashboardApplicationService.Bootstrap(
                new DashboardApplicationService.Summary(
                    92, 1, "42m avg", new OverrideUsage(3, "last 7 days")),
                new DashboardApplicationService.LatestSales(
                    "2026-10-05", new BigDecimal("10865.72"), "agreed"),
                List.of(
                    new DashboardApplicationService.SalesTrend(
                        "2026-10-05", new BigDecimal("10865.72"))),
                List.of(new DashboardApplicationService.ActivityPoint("2026-10-05", 5, 1)),
                List.of(
                    new DashboardApplicationService.TopSeller(
                        "garlic aioli", new BigDecimal("150"), new BigDecimal("380.88"), true))));

    mvc.perform(get("/api/dashboard/bootstrap").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.openConflicts").value(1))
        .andExpect(jsonPath("$.latestSales.total").value(10865.72))
        .andExpect(jsonPath("$.salesTrend[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$.activity[0].clean").value(5))
        .andExpect(jsonPath("$.topSellers[0].name").value("garlic aioli"));
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
