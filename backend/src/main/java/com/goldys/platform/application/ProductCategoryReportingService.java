package com.goldys.platform.application;

import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.classification.AssignmentStatus;
import com.goldys.platform.classification.ProductCategoryQuery;
import com.goldys.platform.classification.ProductCategoryService;
import com.goldys.platform.semantic.AssignmentView;
import com.goldys.platform.semantic.CategoryNode;
import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.DendrogramNode;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.semantic.ProductPoint;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The product-categorisation screen's application service. Authorizes every read and forwards every
 * curation write (which re-checks {@code WRITE} internally) to the classification service.
 *
 * <p>This is the single bridge the {@code api} layer may call: it never reaches the classification
 * package directly. Reads return the public {@code semantic} records; curation writes return public
 * DTOs (never the package-private {@code ProductCategory} entity).
 */
@Service
public class ProductCategoryReportingService {
  private static final ResourceKey RESOURCE = new ResourceKey("products.category");

  private final ProductCategoryQuery query;
  private final ProductCategoryMetricsQuery metrics;
  private final ProductCategoryService categories;
  private final PermissionService permissions;

  public ProductCategoryReportingService(
      ProductCategoryQuery query,
      ProductCategoryMetricsQuery metrics,
      ProductCategoryService categories,
      PermissionService permissions) {
    this.query = query;
    this.metrics = metrics;
    this.categories = categories;
    this.permissions = permissions;
  }

  /** Resolved category sales over the inclusive range, authorized on {@code products.category}. */
  public List<CategorySales> salesByCategory(UserRole role, LocalDate from, LocalDate to) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return metrics.salesByCategory(from, to);
  }

  /** The category taxonomy in display order, each with its assigned product count. */
  public List<CategoryNode> taxonomy(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.taxonomy();
  }

  /** The latest clustering run's dendrogram nodes. */
  public List<DendrogramNode> dendrogram(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.dendrogram();
  }

  /** The latest run's embedding projection joined to current assignments. */
  public List<ProductPoint> embeddingSpace(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.embeddingSpace();
  }

  /**
   * The assignment queue, optionally filtered by status ({@code null}/{@code ""} = all). The status
   * arrives as a string so the {@code api} layer never has to import the classification enum.
   */
  public List<AssignmentView> assignments(UserRole role, String status) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return query.assignments(status == null ? null : AssignmentStatus.valueOf(status));
  }

  /** Creates a category (authorized by the classification service) and returns its public DTO. */
  public CategoryNode create(
      UserRole role, String name, String groupName, int displayOrder, String description) {
    return categories.create(role, name, groupName, displayOrder, description);
  }

  /** Renames a category; the classification service enforces global name uniqueness. */
  public void rename(UserRole role, UUID id, String name) {
    categories.rename(role, id, name);
  }

  /** Moves a category under a (possibly null) group. */
  public void regroup(UserRole role, UUID id, String groupName) {
    categories.regroup(role, id, groupName);
  }

  /** Manually assigns a product to a category ({@code source=MANUAL}). */
  public void move(UserRole role, String productNameKey, UUID categoryId) {
    categories.move(role, productNameKey, categoryId);
  }

  /** Clears a staff override, returning the product to {@code UNASSIGNED}. */
  public void clearOverride(UserRole role, String productNameKey) {
    categories.clearOverride(role, productNameKey);
  }
}
