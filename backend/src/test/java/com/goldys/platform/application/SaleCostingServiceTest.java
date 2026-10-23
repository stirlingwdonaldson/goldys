package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.SaleCostingQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SaleCostingServiceTest {
  @Test
  void requiresBothPermissionsBeforeReadingAndValidatesTheBoundedQuery() {
    var query = mock(SaleCostingQuery.class);
    var permissions = mock(PermissionService.class);
    var service = new SaleCostingService(query, permissions);
    var role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
    var day = LocalDate.of(2026, 10, 21);
    service.costing(role, "SP-100 1021082623", day, day);
    verify(permissions).require(role, new ResourceKey("sale-items.metrics"), PermissionAction.READ);
    verify(permissions).require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    reset(query);
    doThrow(AccessDeniedException.forResource("recipes.cost"))
        .when(permissions)
        .require(role, new ResourceKey("recipes.cost"), PermissionAction.READ);
    assertThatThrownBy(() -> service.costing(role, "SALE", day, day))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(query);
    reset(permissions);
    assertThatThrownBy(() -> service.costing(role, " ", day, day))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.costing(role, "SALE", day.plusDays(1), day))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
