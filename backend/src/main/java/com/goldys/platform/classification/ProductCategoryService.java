package com.goldys.platform.classification;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.CategoryNode;
import com.goldys.platform.semantic.CategorySuggestionView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
  private final CategorySuggestionRepository suggestions;
  private final PermissionService permissions;

  public ProductCategoryService(
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      CategorySuggestionRepository suggestions,
      PermissionService permissions) {
    this.categories = categories;
    this.assignments = assignments;
    this.suggestions = suggestions;
    this.permissions = permissions;
  }

  @Transactional
  public CategoryNode create(
      UserRole role, String name, String groupName, int displayOrder, String description) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    ProductCategory saved =
        categories.save(ProductCategory.create(name, groupName, displayOrder, description));
    // Map to the public DTO here: the entity is package-private, and callers in other packages
    // (the application layer) must never touch it directly.
    return new CategoryNode(saved.id(), saved.name(), saved.groupName(), 0);
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

  /** Adds a staff-authored suggestion (a category the model missed) to the draft. */
  @Transactional
  public CategorySuggestionView addSuggestion(UserRole role, String name, String groupName) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    int order = (int) suggestions.count();
    CategorySuggestion saved =
        suggestions.save(CategorySuggestion.create(null, name, groupName, order, null));
    return new CategorySuggestionView(
        saved.id(),
        saved.runId(),
        saved.name(),
        saved.groupName(),
        saved.displayOrder(),
        saved.hint());
  }

  /** Renames and/or regroups a suggestion. A null field is left unchanged. */
  @Transactional
  public void updateSuggestion(UserRole role, UUID id, String name, String groupName) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    CategorySuggestion suggestion =
        suggestions
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("No suggestion with id " + id));
    if (name != null) {
      suggestion.rename(name);
    }
    if (groupName != null) {
      suggestion.regroup(groupName);
    }
  }

  /** Removes a suggestion from the draft. */
  @Transactional
  public void deleteSuggestion(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    suggestions.deleteById(id);
  }

  /**
   * Promotes the draft to confirmed categories: each suggestion whose name is not already a
   * confirmed category becomes a {@code product_category} row (in {@code display_order}); names
   * that already exist are skipped. The draft is then cleared. Products are not assigned here — Jev
   * does that on the next {@code ASSIGN}/{@code FULL} run.
   */
  @Transactional
  public List<CategoryNode> applySuggestions(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    List<CategoryNode> created = new ArrayList<>();
    for (CategorySuggestion suggestion : suggestions.findAllByOrderByDisplayOrderAsc()) {
      if (categories.findByName(suggestion.name()).isEmpty()) {
        ProductCategory category =
            categories.save(
                ProductCategory.create(
                    suggestion.name(), suggestion.groupName(), suggestion.displayOrder(), null));
        created.add(new CategoryNode(category.id(), category.name(), category.groupName(), 0));
      }
    }
    suggestions.deleteAll();
    return created;
  }
}
