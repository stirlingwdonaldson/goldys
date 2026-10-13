package com.goldys.platform.classification;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-facing curation of the product category taxonomy and of product→category assignments.
 *
 * <p>Every mutation is permission-gated on {@code products.category} {@code WRITE}. Category names
 * are globally unique, so {@link #rename} rejects a name already taken by another category.
 * Assignments are keyed directly on {@code product_name_key} and updated in place (see {@link
 * ProductCategoryAssignment}).
 */
@Service
public class ProductCategoryService {

  private static final ResourceKey RESOURCE = new ResourceKey("products.category");
  private static final Clock CLOCK = Clock.systemUTC();

  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;
  private final PermissionService permissions;

  public ProductCategoryService(
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      PermissionService permissions) {
    this.categories = categories;
    this.assignments = assignments;
    this.permissions = permissions;
  }

  @Transactional
  public ProductCategory create(
      UserRole role, String name, String groupName, int displayOrder, String description) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return categories.save(ProductCategory.create(name, groupName, displayOrder, description));
  }

  @Transactional
  public void rename(UserRole role, UUID id, String name) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    ProductCategory category =
        categories
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("No category with id " + id));
    categories
        .findByName(name)
        .filter(other -> !other.id().equals(id))
        .ifPresent(
            other -> {
              throw new IllegalArgumentException("Category name already exists: " + name);
            });
    category.rename(name);
  }

  @Transactional
  public void regroup(UserRole role, UUID id, String groupName) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    ProductCategory category =
        categories
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("No category with id " + id));
    category.regroup(groupName);
  }

  @Transactional
  public void move(UserRole role, String productNameKey, UUID categoryId) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    Instant now = CLOCK.instant();
    ProductCategoryAssignment assignment =
        assignments
            .findById(productNameKey)
            .orElseGet(() -> ProductCategoryAssignment.create(productNameKey, now));
    assignment.markAssigned(categoryId, null, AssignmentSource.MANUAL, now);
    assignments.save(assignment);
  }

  /**
   * Removes a staff override on a product, returning it to the {@code UNASSIGNED} state so Jev can
   * reclassify it on the next run.
   *
   * <p>A move is an in-place update of the single {@code product_name_key} row — there is no
   * separate manual row to delete — so clearing means resetting that row via {@link
   * ProductCategoryAssignment#markUnassigned}. No-op when the product has no assignment row yet.
   */
  @Transactional
  public void clearOverride(UserRole role, String productNameKey) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    assignments
        .findById(productNameKey)
        .ifPresent(assignment -> assignment.markUnassigned(CLOCK.instant()));
  }
}
