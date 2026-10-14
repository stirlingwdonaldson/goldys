package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.ReviewReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.ReviewFilter;
import com.goldys.platform.semantic.ReviewOverview;
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

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ReviewReportingService reporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void overviewReturns200AndDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.overview(any(), any(), any()))
        .thenReturn(new ReviewOverview(120, 4.2, 80, 25, 15, 0.87));

    mvc.perform(
            get("/api/reviews/overview")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(120))
        .andExpect(jsonPath("$.averageRating").value(4.2))
        .andExpect(jsonPath("$.positive").value(80))
        .andExpect(jsonPath("$.neutral").value(25))
        .andExpect(jsonPath("$.negative").value(15))
        .andExpect(jsonPath("$.wouldRecommendPct").value(0.87));

    verify(reporting).overview(ownerRole(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
  }

  @Test
  void reviewsMapsFilterParams() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.reviews(any(), any())).thenReturn(List.of());

    mvc.perform(
            get("/api/reviews")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .param("source", "opentable")
                .param("sentiment", "POSITIVE")
                .param("aspect", "food")
                .param("minRating", "4")
                .param("q", "pizza")
                .with(authenticated(owner())))
        .andExpect(status().isOk());

    verify(reporting)
        .reviews(
            ownerRole(),
            new ReviewFilter(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "opentable",
                "POSITIVE",
                "food",
                4,
                "pizza"));
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
