package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.goldys.platform.auth.*;
import com.goldys.platform.classification.ProductReportingMappingService;
import com.goldys.platform.semantic.ClassifiedWastageQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductReportingReviewServiceTest {
  private final PermissionService permissions = mock(PermissionService.class);
  private final ClassifiedWastageQuery query = mock(ClassifiedWastageQuery.class);
  private final ProductReportingMappingService mappings =
      mock(ProductReportingMappingService.class);
  private final ProductReportingReviewService service =
      new ProductReportingReviewService(mappings, permissions, query);
  private final UserRole role =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("MANAGER"));
  private final LocalDate date = LocalDate.of(2026, 11, 3);

  @Test
  void cost_and_category_access_cannot_read_stock_without_stock_permission() {
    // Other resource checks are allowed; only stock READ is denied for this role.
    doThrow(new AccessDeniedException("stock denied"))
        .when(permissions)
        .require(role, new ResourceKey("inventory.stock"), PermissionAction.READ);
    assertThatThrownBy(() -> service.wastage(role, date, date, 0, 50))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("stock");
    verifyNoInteractions(query, mappings);
  }

  @Test
  void all_three_permissions_precede_the_query() {
    service.wastage(role, date, date, 0, 50);
    var order = inOrder(permissions, query);
    order
        .verify(permissions)
        .require(role, new ResourceKey("inventory.stock"), PermissionAction.READ);
    order
        .verify(permissions)
        .require(role, new ResourceKey("inventory.cost"), PermissionAction.READ);
    order
        .verify(permissions)
        .require(role, new ResourceKey("products.category"), PermissionAction.READ);
    order.verify(query).lines(date, date, 0, 50);
  }
}
