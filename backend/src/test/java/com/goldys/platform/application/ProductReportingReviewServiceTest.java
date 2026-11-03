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
  private final com.goldys.platform.semantic.ReportingReviewQuery review =
      mock(com.goldys.platform.semantic.ReportingReviewQuery.class);
  private final com.goldys.platform.semantic.ReportingSalesQuery sales =
      mock(com.goldys.platform.semantic.ReportingSalesQuery.class);
  private final ProductReportingReviewService service =
      new ProductReportingReviewService(mappings, permissions, query, review, sales);
  private final UserRole role =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("MANAGER"));
  private final LocalDate date = LocalDate.of(2026, 11, 3);

  @Test
  void queue_authorizes_every_resource_before_reading() {
    for (String resource :
        java.util.List.of(
            "products.category", "reconciliation.sales", "inventory.stock", "inventory.cost")) {
      reset(permissions);
      doThrow(new AccessDeniedException("denied"))
          .when(permissions)
          .require(role, new ResourceKey(resource), PermissionAction.READ);
      assertThatThrownBy(
              () ->
                  service.queue(
                      role,
                      com.goldys.platform.semantic.ReportingReviewQuery.Domain.SALES,
                      date,
                      date,
                      0,
                      50))
          .isInstanceOf(AccessDeniedException.class);
      verifyNoInteractions(query, mappings, review, sales);
    }
  }

  @Test
  void queue_rejects_unbounded_ranges_and_pagination_before_queries() {
    for (int[] bounds :
        new int[][] {
          {92, 0, 50}, {0, -1, 50}, {0, 10001, 50}, {0, 0, 201}, {0, 0, 0}, {-1, 0, 50}
        }) {
      assertThatThrownBy(
              () ->
                  service.queue(
                      role,
                      com.goldys.platform.semantic.ReportingReviewQuery.Domain.SALES,
                      date,
                      date.plusDays(bounds[0]),
                      bounds[1],
                      bounds[2]))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(review, sales);
  }

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
