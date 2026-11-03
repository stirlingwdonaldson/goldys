package com.goldys.platform.classification;

import com.goldys.platform.semantic.CategorySales;
import com.goldys.platform.semantic.FoodBevSplit;
import com.goldys.platform.semantic.ProductCategoryMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductSalesMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link ProductCategoryMetricsQuery} backed by resolved product sales joined to the curated
 * category assignment in memory (pub scale). {@code ProductSalesMetric.productName} is already the
 * normalized {@code product_name_key} (Ruling B), so the join is direct.
 */
@Service
public class ResolvedProductCategoryQuery implements ProductCategoryMetricsQuery {

  private static final String UNCATEGORISED = "Uncategorised";
  private final ProductMetricsQuery productMetrics;
  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;
  private final com.goldys.platform.semantic.ReportingSalesQuery reportingSales;

  public ResolvedProductCategoryQuery(
      ProductMetricsQuery productMetrics,
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments,
      com.goldys.platform.semantic.ReportingSalesQuery reportingSales) {
    this.productMetrics = productMetrics;
    this.categories = categories;
    this.assignments = assignments;
    this.reportingSales = reportingSales;
  }

  @Override
  public List<CategorySales> salesByCategory(LocalDate from, LocalDate to) {
    // Sum quantity/amount per normalized product name across the range.
    Map<String, MutableTotal> byProduct = new LinkedHashMap<>();
    for (ProductSalesMetric metric : productMetrics.productSales(from, to)) {
      MutableTotal total =
          byProduct.computeIfAbsent(metric.productName(), key -> new MutableTotal());
      total.quantity = nullSafeAdd(total.quantity, metric.quantitySold());
      total.amount = nullSafeAdd(total.amount, metric.amount());
    }

    Classification classification = resolveClassification();

    // Roll each product up into its leaf category; unassigned products (or products whose category
    // was deleted) share the single "Uncategorised" bucket with a null group.
    Map<CategoryKey, MutableTotal> byCategory = new LinkedHashMap<>();
    for (Map.Entry<String, MutableTotal> entry : byProduct.entrySet()) {
      ProductCategory leaf = classification.leafFor(entry.getKey());
      String group = leaf == null ? null : leaf.groupName();
      String category = leaf == null ? UNCATEGORISED : leaf.name();
      MutableTotal total =
          byCategory.computeIfAbsent(new CategoryKey(group, category), k -> new MutableTotal());
      total.quantity = nullSafeAdd(total.quantity, entry.getValue().quantity);
      total.amount = nullSafeAdd(total.amount, entry.getValue().amount);
    }

    List<CategorySales> out = new ArrayList<>(byCategory.size());
    for (Map.Entry<CategoryKey, MutableTotal> entry : byCategory.entrySet()) {
      out.add(
          new CategorySales(
              entry.getKey().group(),
              entry.getKey().category(),
              entry.getValue().quantity,
              entry.getValue().amount));
    }
    return out;
  }

  @Override
  public List<FoodBevSplit> dailyFoodBev(LocalDate from, LocalDate to) {
    return reportingSales.dailyFoodBev(from, to);
  }

  /** Builds the approved browsing assignment lookup once for category sales totals. */
  private Classification resolveClassification() {
    Map<String, ProductCategoryAssignment> assignmentByProduct = new HashMap<>();
    for (ProductCategoryAssignment assignment : assignments.findAll()) {
      assignmentByProduct.put(assignment.productNameKey(), assignment);
    }

    Map<UUID, ProductCategory> categoryById = new HashMap<>();
    for (ProductCategory category : categories.findAll()) {
      categoryById.put(category.id(), category);
    }

    return new Classification(assignmentByProduct, categoryById);
  }

  /** Adds compatible known values. Once an input is unknown, the category total remains unknown. */
  private static BigDecimal nullSafeAdd(BigDecimal current, BigDecimal add) {
    if (current == null) {
      return null;
    }
    return add == null ? null : current.add(add);
  }

  /** The resolved product → category/group mapping for one query. */
  private record Classification(
      Map<String, ProductCategoryAssignment> assignmentByProduct,
      Map<UUID, ProductCategory> categoryById) {

    /** The leaf category of a product, or null when unassigned or its category was deleted. */
    ProductCategory leafFor(String productName) {
      ProductCategoryAssignment assignment = assignmentByProduct.get(productName);
      return assignment == null
              || assignment.status() != AssignmentStatus.ASSIGNED
              || assignment.categoryId() == null
          ? null
          : categoryById.get(assignment.categoryId());
    }
  }

  /** Composite grouping key for a leaf category within its (nullable) group. */
  private record CategoryKey(String group, String category) {}

  private static final class MutableTotal {
    BigDecimal quantity = BigDecimal.ZERO;
    BigDecimal amount = BigDecimal.ZERO;
  }
}
