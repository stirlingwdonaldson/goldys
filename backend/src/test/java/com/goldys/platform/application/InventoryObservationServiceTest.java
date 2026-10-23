package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.semantic.InventoryObservationQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryObservationServiceTest {
  final PermissionService permissions = mock(PermissionService.class);
  final InventoryObservationQuery query = mock(InventoryObservationQuery.class);
  final InventoryObservationService service = new InventoryObservationService(query, permissions);
  final UserRole role = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  final LocalDate date = LocalDate.of(2026, 10, 15);

  @Test
  void requiresBothPermissionsBeforeReadingAndRetainsNotesOnAnEmptyPage() {
    when(query.observations(date, date, "ALL", 0, 51)).thenReturn(List.of());
    var result = service.observations(role, date, date, "ALL", 0, 50);
    verify(permissions).require(role, new ResourceKey("inventory.stock"), PermissionAction.READ);
    verify(permissions).require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThat(result.notices()).anyMatch(s -> s.contains("Incomplete"));
    assertThat(result.observations()).isEmpty();
  }

  @Test
  void costDenialCannotReturnObservationsEvenWithStockRead() {
    doThrow(new AccessDeniedException("denied"))
        .when(permissions)
        .require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    assertThatThrownBy(() -> service.observations(role, date, date, "ALL", 0, 50))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(query);
  }

  @Test
  void invalidRangeOrPaginationCannotReachStorage() {
    assertThatThrownBy(() -> service.observations(role, date.plusDays(1), date, "ALL", 0, 50))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.observations(role, date, date, "ALL", 0, 201))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.observations(role, date, date, "OTHER", 0, 50))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(query);
  }
}
