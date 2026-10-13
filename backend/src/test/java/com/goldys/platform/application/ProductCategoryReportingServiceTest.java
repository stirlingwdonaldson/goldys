package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.classification.ProductCategoryQuery;
import com.goldys.platform.classification.ProductCategoryService;
import com.goldys.platform.classification.ProductClassificationService;
import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.ClassificationRunView;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application-layer coverage: read authorization and denial propagation. */
class ProductCategoryReportingServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Test
  void authorizesAndReturnsSales() {
    ProductCategoryMetricsQuery metrics = mock(ProductCategoryMetricsQuery.class);
    CategorySales expected =
        new CategorySales("Food", "Burgers", new BigDecimal("2"), new BigDecimal("30.00"));
    LocalDate from = LocalDate.of(2026, 10, 1);
    LocalDate to = LocalDate.of(2026, 10, 31);
    when(metrics.salesByCategory(from, to)).thenReturn(List.of(expected));
    PermissionService permissions = mock(PermissionService.class);
    ProductCategoryReportingService service =
        new ProductCategoryReportingService(
            mock(ProductCategoryQuery.class),
            metrics,
            mock(ProductCategoryService.class),
            mock(ProductClassificationService.class),
            permissions);

    assertThat(service.salesByCategory(OWNER, from, to)).containsExactly(expected);
    verify(permissions).require(OWNER, new ResourceKey("products.category"), PermissionAction.READ);
  }

  @Test
  void deniesWithoutPermission() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("products.category"))
        .when(permissions)
        .require(any(), any(), any());
    ProductCategoryReportingService service =
        new ProductCategoryReportingService(
            mock(ProductCategoryQuery.class),
            mock(ProductCategoryMetricsQuery.class),
            mock(ProductCategoryService.class),
            mock(ProductClassificationService.class),
            permissions);

    assertThatThrownBy(
            () ->
                service.salesByCategory(OWNER, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void runClassificationRequiresWriteThenDelegates() {
    PermissionService permissions = mock(PermissionService.class);
    ProductClassificationService classification = mock(ProductClassificationService.class);
    ProductCategoryReportingService service = service(classification, permissions);

    service.runClassification(OWNER, "CLUSTER");
    service.runClassification(OWNER, "assign");
    service.runClassification(OWNER, "Full");

    verify(permissions, times(3))
        .require(OWNER, new ResourceKey("products.category"), PermissionAction.WRITE);
    verify(classification).runClustering();
    verify(classification).runAssignment();
    verify(classification).runFull();
  }

  @Test
  void runClassificationRejectsUnknownKind() {
    ProductCategoryReportingService service =
        service(mock(ProductClassificationService.class), mock(PermissionService.class));

    assertThatThrownBy(() -> service.runClassification(OWNER, "BOGUS"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listRunsRequiresReadThenDelegates() {
    PermissionService permissions = mock(PermissionService.class);
    ProductClassificationService classification = mock(ProductClassificationService.class);
    ClassificationRunView view =
        new ClassificationRunView(
            UUID.randomUUID(),
            "FULL",
            "SUCCEEDED",
            5,
            Instant.parse("2026-10-13T00:00:00Z"),
            Instant.parse("2026-10-13T00:01:00Z"),
            null);
    when(classification.listRuns()).thenReturn(List.of(view));
    ProductCategoryReportingService service = service(classification, permissions);

    assertThat(service.listRuns(OWNER)).containsExactly(view);
    verify(permissions).require(OWNER, new ResourceKey("products.category"), PermissionAction.READ);
  }

  @Test
  void createRejectsBlankName() {
    ProductCategoryReportingService service =
        service(mock(ProductClassificationService.class), mock(PermissionService.class));

    assertThatThrownBy(() -> service.create(OWNER, null, "Drinks", 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.create(OWNER, "  ", "Drinks", 0, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ProductCategoryReportingService service(
      ProductClassificationService classification, PermissionService permissions) {
    return new ProductCategoryReportingService(
        mock(ProductCategoryQuery.class),
        mock(ProductCategoryMetricsQuery.class),
        mock(ProductCategoryService.class),
        classification,
        permissions);
  }
}
