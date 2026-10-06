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
import com.goldys.platform.canonical.CanonicalDailySalesQuery;
import com.goldys.platform.canonical.DailySalesView;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.reconciliation.ResolvedDailySalesQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SalesController.class)
@Import(SecurityConfig.class)
class SalesControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CanonicalDailySalesQuery dailySales;
  @MockitoBean ResolvedDailySalesQuery resolvedDailySales;
  @MockitoBean CurrentUserService currentUser;
  @MockitoBean PermissionService permissions;

  @Test
  void returnsDailySalesNewestFirst() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(dailySales.currentDailySales())
        .thenReturn(
            List.of(
                new DailySalesView(
                    "CTB",
                    LocalDate.of(2026, 10, 4),
                    new BigDecimal("29605.13"),
                    new BigDecimal("2689.54"),
                    new BigDecimal("26915.60"),
                    Instant.EPOCH),
                new DailySalesView(
                    "CTB",
                    LocalDate.of(2026, 10, 5),
                    new BigDecimal("10865.72"),
                    new BigDecimal("985.44"),
                    new BigDecimal("9880.28"),
                    Instant.EPOCH)));

    mvc.perform(get("/api/sales/daily").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$[0].source").value("CTB"))
        .andExpect(jsonPath("$[1].date").value("2026-10-04"));
  }

  @Test
  void latestReturnsNullsWhenThereIsNoData() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(resolvedDailySales.latest()).thenReturn(Optional.empty());

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value(nullValue()))
        .andExpect(jsonPath("$.total").value(nullValue()))
        .andExpect(jsonPath("$.authoritativeSource").value(nullValue()));
  }

  @Test
  void latestReturnsTheResolvedTotal() throws Exception {
    LocalDate date = LocalDate.of(2026, 10, 5);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(resolvedDailySales.latest())
        .thenReturn(
            Optional.of(
                new ResolvedDailySalesQuery.ResolvedDailySalesView(
                    date, new BigDecimal("10865.72"), "agreed", "agreed", false)));

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-05"))
        .andExpect(jsonPath("$.total").value(10865.72))
        .andExpect(jsonPath("$.authoritativeSource").value("agreed"));
  }

  @Test
  void latestReturnsNullTotalWhenTheLatestDateIsUnresolved() throws Exception {
    LocalDate date = LocalDate.of(2026, 10, 5);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(resolvedDailySales.latest())
        .thenReturn(
            Optional.of(
                new ResolvedDailySalesQuery.ResolvedDailySalesView(
                    date, null, "conflict", null, true)));

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-05"))
        .andExpect(jsonPath("$.total").value(nullValue()))
        .andExpect(jsonPath("$.authoritativeSource").value(nullValue()));
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
