package com.goldys.platform.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.SalesReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.DataPage;
import java.math.BigDecimal;
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

  @MockitoBean SalesReportingService salesReporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void returnsDailySalesNewestFirst() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(salesReporting.dailySales(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(
            new DataPage<>(
                List.of(
                    new SalesReportingService.DailySalesRow(
                        "2026-10-05",
                        "CTB",
                        new BigDecimal("10865.72"),
                        new BigDecimal("985.44"),
                        new BigDecimal("9880.28")),
                    new SalesReportingService.DailySalesRow(
                        "2026-10-04",
                        "CTB",
                        new BigDecimal("29605.13"),
                        new BigDecimal("2689.54"),
                        new BigDecimal("26915.60"))),
                2,
                0,
                50));

    mvc.perform(get("/api/sales/daily").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$.items[0].source").value("CTB"))
        .andExpect(jsonPath("$.items[1].date").value("2026-10-04"))
        .andExpect(jsonPath("$.total").value(2));
  }

  @Test
  void dailyForwardsDateBoundsAndPagingToTheService() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(salesReporting.dailySales(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new DataPage<>(List.of(), 0, 0, 50));

    mvc.perform(
            get("/api/sales/daily")
                .param("from", "2026-10-01")
                .param("to", "2026-10-05")
                .param("page", "2")
                .param("size", "25")
                .with(authenticated(owner())))
        .andExpect(status().isOk());

    verify(salesReporting)
        .dailySales(
            any(), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 5)), eq(2), eq(25));
  }

  @Test
  void latestReturnsNullsWhenThereIsNoData() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(salesReporting.latestTradingDay(any()))
        .thenReturn(new SalesReportingService.LatestSales(null, null, null));

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value(nullValue()))
        .andExpect(jsonPath("$.total").value(nullValue()))
        .andExpect(jsonPath("$.authoritativeSource").value(nullValue()));
  }

  @Test
  void latestReturnsTheResolvedTotal() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(salesReporting.latestTradingDay(any()))
        .thenReturn(
            new SalesReportingService.LatestSales(
                "2026-10-05", new BigDecimal("10865.72"), "agreed"));

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-05"))
        .andExpect(jsonPath("$.total").value(10865.72))
        .andExpect(jsonPath("$.authoritativeSource").value("agreed"));
  }

  @Test
  void latestReturnsNullTotalWhenTheLatestDateIsUnresolved() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(salesReporting.latestTradingDay(any()))
        .thenReturn(new SalesReportingService.LatestSales("2026-10-05", null, null));

    mvc.perform(get("/api/sales/latest").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-05"))
        .andExpect(jsonPath("$.total").value(nullValue()))
        .andExpect(jsonPath("$.authoritativeSource").value(nullValue()));
  }

  @Test
  void invalidDateBoundReturnsBadRequest() throws Exception {
    mvc.perform(get("/api/sales/daily").param("from", "not-a-date").with(authenticated(owner())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Invalid value for 'from'"));
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
