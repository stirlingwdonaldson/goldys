package com.goldys.platform.api;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(SalesController.class)
@Import(SecurityConfig.class)
class SalesControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CanonicalDailySalesQuery dailySales;
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
