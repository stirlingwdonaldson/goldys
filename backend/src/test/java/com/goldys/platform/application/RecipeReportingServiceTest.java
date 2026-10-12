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
import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void costingRequiresReadPermission() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("recipes.cost"))
        .when(permissions)
        .require(any(), any(), any());
    RecipeReportingService service =
        new RecipeReportingService(mock(RecipeMetricsQuery.class), permissions);

    assertThatThrownBy(() -> service.costing(OWNER)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void costingReturnsTheResolvedList() {
    RecipeMetricsQuery metrics = mock(RecipeMetricsQuery.class);
    RecipeCosting expected =
        new RecipeCosting(
            1,
            "Parma",
            "Mains",
            new BigDecimal("6.00"),
            new BigDecimal("7.20"),
            new BigDecimal("1.20"),
            new BigDecimal("18.50"),
            new BigDecimal("12.50"),
            new BigDecimal("0.6757"));
    when(metrics.costing()).thenReturn(List.of(expected));
    PermissionService permissions = mock(PermissionService.class);
    RecipeReportingService service = new RecipeReportingService(metrics, permissions);

    assertThat(service.costing(OWNER)).containsExactly(expected);
    verify(permissions).require(OWNER, new ResourceKey("recipes.cost"), PermissionAction.READ);
  }
}
