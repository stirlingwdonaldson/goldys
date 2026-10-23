package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.RecipeCompositionQuery;
import org.junit.jupiter.api.Test;

class RecipeCompositionReportingServiceTest {
  @Test
  void compositionRequiresBothCostAndIngredientPermissionsBeforeReading() {
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var permissions = mock(PermissionService.class);
    var query = mock(RecipeCompositionQuery.class);
    doThrow(AccessDeniedException.forResource("recipes.ingredients"))
        .when(permissions)
        .require(role, new ResourceKey("recipes.ingredients"), PermissionAction.READ);
    var service = new RecipeCompositionReportingService(query, permissions);
    assertThatThrownBy(() -> service.composition(463, role))
        .isInstanceOf(AccessDeniedException.class);
    verify(permissions).require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    verifyNoInteractions(query);
  }
}
