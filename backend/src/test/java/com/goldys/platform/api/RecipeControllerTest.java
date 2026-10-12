package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.RecipeReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.RecipeCosting;
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

@WebMvcTest(RecipeController.class)
@Import(SecurityConfig.class)
class RecipeControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean RecipeReportingService reporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void listReturnsTheCosting() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.costing(any()))
        .thenReturn(
            List.of(
                new RecipeCosting(
                    1,
                    "Parma",
                    "Mains",
                    new BigDecimal("6.00"),
                    new BigDecimal("7.20"),
                    new BigDecimal("1.20"),
                    new BigDecimal("18.50"),
                    new BigDecimal("12.50"),
                    new BigDecimal("0.6757"))));

    mvc.perform(get("/api/recipes").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].recipeId").value(1))
        .andExpect(jsonPath("$[0].name").value("Parma"))
        .andExpect(jsonPath("$[0].folder").value("Mains"))
        .andExpect(jsonPath("$[0].menuPrice").value(18.50))
        .andExpect(jsonPath("$[0].margin").value(12.50));
  }

  @Test
  void listReturnsEmptyArrayWhenNoRecipes() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.costing(any())).thenReturn(List.of());

    mvc.perform(get("/api/recipes").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
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
