package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.ReservationReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.CoversMetric;
import com.goldys.platform.semantic.ReservationSummary;
import java.math.BigDecimal;
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

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ReservationReportingService reporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void summaryReturnsTheResolvedSummary() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.summary(any(), any()))
        .thenReturn(
            Optional.of(
                new ReservationSummary(
                    LocalDate.of(2026, 9, 20),
                    10,
                    8,
                    32,
                    1,
                    1,
                    2,
                    new BigDecimal("4.0000"),
                    new BigDecimal("0.1000"),
                    new BigDecimal("0.8000"))));

    mvc.perform(
            get("/api/reservations/summary")
                .param("date", "2026-09-20")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-09-20"))
        .andExpect(jsonPath("$.bookings").value(10))
        .andExpect(jsonPath("$.covers").value(32))
        .andExpect(jsonPath("$.noShows").value(1));
  }

  @Test
  void summaryReturnsNoContentWhenThereIsNoData() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.summary(any(), any())).thenReturn(Optional.empty());

    mvc.perform(
            get("/api/reservations/summary")
                .param("date", "2026-09-20")
                .with(authenticated(owner())))
        .andExpect(status().isNoContent());
  }

  @Test
  void coversReturnsTheResolvedList() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.dailyCovers(any(), any(), any()))
        .thenReturn(List.of(new CoversMetric(LocalDate.of(2026, 9, 20), 310, "OPENTABLE", false)));

    mvc.perform(
            get("/api/reservations/covers")
                .param("from", "2026-09-20")
                .param("to", "2026-09-20")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-09-20"))
        .andExpect(jsonPath("$[0].covers").value(310));
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
