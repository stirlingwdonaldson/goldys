package com.goldys.platform.classification;

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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Curation-service coverage: permission gating, taxonomy edits, and manual assignment overrides.
 */
class ProductCategoryServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private ProductCategoryService service(
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      PermissionService permissions) {
    return new ProductCategoryService(categories, assignments, permissions);
  }

  @Test
  void createPersistsCategory() {
    ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ProductCategoryService service =
        service(
            categories,
            mock(ProductCategoryAssignmentRepository.class),
            mock(PermissionService.class));

    ProductCategory created = service.create(OWNER, "Cocktails", "Drinks", 0, null);

    assertThat(created.name()).isEqualTo("Cocktails");
    assertThat(created.groupName()).isEqualTo("Drinks");
    assertThat(created.displayOrder()).isEqualTo(0);
    assertThat(created.description()).isNull();
    verify(categories).save(created);
  }

  @Test
  void renameRejectsDuplicateName() {
    ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ProductCategoryService service =
        service(
            categories,
            mock(ProductCategoryAssignmentRepository.class),
            mock(PermissionService.class));

    ProductCategory existing = service.create(OWNER, "Draft Beer", "Drinks", 0, null);
    ProductCategory target = ProductCategory.create("Lager", "Drinks", 1, null);
    when(categories.findById(target.id())).thenReturn(Optional.of(target));
    when(categories.findByName("Draft Beer")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.rename(OWNER, target.id(), "Draft Beer"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Draft Beer");
  }

  @Test
  void regroupClearsGroup() {
    ProductCategory category = ProductCategory.create("Cocktails", "Drinks", 0, null);
    ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    when(categories.findById(category.id())).thenReturn(Optional.of(category));
    ProductCategoryService service =
        service(
            categories,
            mock(ProductCategoryAssignmentRepository.class),
            mock(PermissionService.class));

    service.regroup(OWNER, category.id(), null);

    assertThat(category.groupName()).isNull();
  }

  @Test
  void moveMarksManualAndAssigned() {
    ProductCategoryAssignmentRepository assignments =
        mock(ProductCategoryAssignmentRepository.class);
    when(assignments.findById("kids-fish")).thenReturn(Optional.empty());
    ProductCategoryService service =
        service(mock(ProductCategoryRepository.class), assignments, mock(PermissionService.class));
    UUID categoryId = UUID.randomUUID();

    service.move(OWNER, "kids-fish", categoryId);

    ArgumentCaptor<ProductCategoryAssignment> captor =
        ArgumentCaptor.forClass(ProductCategoryAssignment.class);
    verify(assignments).save(captor.capture());
    ProductCategoryAssignment saved = captor.getValue();
    assertThat(saved.productNameKey()).isEqualTo("kids-fish");
    assertThat(saved.categoryId()).isEqualTo(categoryId);
    assertThat(saved.source()).isEqualTo(AssignmentSource.MANUAL);
    assertThat(saved.status()).isEqualTo(AssignmentStatus.ASSIGNED);
  }

  @Test
  void clearOverrideResetsToUnassigned() {
    ProductCategoryAssignment assignment =
        ProductCategoryAssignment.create("kids-fish", Instant.now());
    assignment.markAssigned(UUID.randomUUID(), null, AssignmentSource.MANUAL, Instant.now());
    ProductCategoryAssignmentRepository assignments =
        mock(ProductCategoryAssignmentRepository.class);
    when(assignments.findById("kids-fish")).thenReturn(Optional.of(assignment));
    ProductCategoryService service =
        service(mock(ProductCategoryRepository.class), assignments, mock(PermissionService.class));

    service.clearOverride(OWNER, "kids-fish");

    assertThat(assignment.status()).isEqualTo(AssignmentStatus.UNASSIGNED);
    assertThat(assignment.categoryId()).isNull();
    assertThat(assignment.source()).isNull();
  }

  @Test
  void requiresWritePermission() {
    PermissionService permissions = mock(PermissionService.class);
    doThrow(AccessDeniedException.forResource("products.category"))
        .when(permissions)
        .require(any(), any(), any());
    ProductCategoryService service =
        service(
            mock(ProductCategoryRepository.class),
            mock(ProductCategoryAssignmentRepository.class),
            permissions);

    assertThatThrownBy(() -> service.create(OWNER, "Cocktails", "Drinks", 0, null))
        .isInstanceOf(AccessDeniedException.class);

    verify(permissions)
        .require(OWNER, new ResourceKey("products.category"), PermissionAction.WRITE);
  }
}
