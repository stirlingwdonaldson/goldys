package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InvoiceGraphQuery;
import com.goldys.platform.semantic.SupplierGraphNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceGraphServiceTest {

  private final InvoiceGraphQuery query = mock(InvoiceGraphQuery.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final InvoiceGraphService service = new InvoiceGraphService(query, permissions);

  private static UserRole role() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  @Test
  void delegatesSuppliersAfterAuthorizing() {
    when(query.suppliers(any(), any()))
        .thenReturn(List.of(new SupplierGraphNode("A. Foods", 1, BigDecimal.ONE)));

    List<SupplierGraphNode> result =
        service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThat(result).hasSize(1);
    verify(permissions).require(any(), any(), any());
  }

  @Test
  void rejectsReadsWithoutInventoryPermission() {
    doThrow(new RuntimeException("denied")).when(permissions).require(any(), any(), any());

    assertThatThrownBy(
            () -> service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .isInstanceOf(RuntimeException.class);
    verify(query, never()).suppliers(any(), any());
  }

  @Test
  void requiresReadOnTheInventoryCostResource() {
    when(query.suppliers(any(), any())).thenReturn(List.of());

    service.suppliers(role(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    verify(permissions).require(role(), new ResourceKey("inventory.cost"), PermissionAction.READ);
  }
}
