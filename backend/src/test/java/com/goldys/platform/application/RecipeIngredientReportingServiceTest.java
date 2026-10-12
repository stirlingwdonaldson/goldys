package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeIngredientReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void ingredientsRequiresReadPermission() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("recipes.ingredients"))
        .when(permissions)
        .require(any(), any(), any());
    RecipeIngredientReportingService service =
        new RecipeIngredientReportingService(mock(RecipeIngredientMetricsQuery.class), permissions);

    assertThatThrownBy(() -> service.ingredients(OWNER, 463))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void ingredientsReturnsTheResolvedList() {
    RecipeIngredientMetricsQuery metrics = mock(RecipeIngredientMetricsQuery.class);
    RecipeIngredient expected =
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
            "Acme");
    when(metrics.ingredientsFor(463)).thenReturn(List.of(expected));
    PermissionService permissions = mock(PermissionService.class);
    RecipeIngredientReportingService service =
        new RecipeIngredientReportingService(metrics, permissions);

    assertThat(service.ingredients(OWNER, 463)).containsExactly(expected);
    verify(permissions)
        .require(OWNER, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
  }
}
