package com.goldys.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.RecipeIngredientReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.RecipeIngredient;
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

@WebMvcTest(RecipeIngredientController.class)
@Import(SecurityConfig.class)
class RecipeIngredientControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean RecipeIngredientReportingService reporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void ingredientsReturnsTheLines() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.ingredients(any(), eq(463)))
        .thenReturn(
            List.of(
                new RecipeIngredient(
                    463,
                    463,
                    "Cheese Burger",
                    false,
                    101,
                    42,
                    "BUN",
                    "Brioche bun",
                    new BigDecimal("1.0000"),
                    "grams",
                    new BigDecimal("6.00"),
                    new BigDecimal("2.00"),
                    "Acme")));

    mvc.perform(get("/api/recipes/463/ingredients").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].parentRecipeId").value(463))
        .andExpect(jsonPath("$[0].stockDescription").value("Brioche bun"))
        .andExpect(jsonPath("$[0].isSubRecipe").value(false));
  }

  @Test
  void ingredientsReturnsEmptyArrayForRecipeWithNoLines() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.ingredients(any(), eq(999))).thenReturn(List.of());

    mvc.perform(get("/api/recipes/999/ingredients").with(authenticated(owner())))
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
