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
  private static final String FOOD_GROUP = "Food";

  private final ProductMetricsQuery productMetrics;
  private final ProductCategoryRepository categories;
  private final ProductCategoryAssignmentRepository assignments;

  public ResolvedProductCategoryQuery(
      ProductMetricsQuery productMetrics,
      ProductCategoryRepository categories,
      ProductCategoryAssignmentRepository assignments) {
    this.productMetrics = productMetrics;
    this.categories = categories;
    this.assignments = assignments;
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

    Map<String, ProductCategoryAssignment> assignmentByProduct = new HashMap<>();
    for (ProductCategoryAssignment assignment : assignments.findAll()) {
      assignmentByProduct.put(assignment.productNameKey(), assignment);
    }

    Map<UUID, ProductCategory> categoryById = new HashMap<>();
    for (ProductCategory category : categories.findAll()) {
      categoryById.put(category.id(), category);
    }

    // Roll each product up into its leaf category; unassigned products (or products whose category
    // was deleted) share the single "Uncategorised" bucket with a null group.
    Map<CategoryKey, MutableTotal> byCategory = new LinkedHashMap<>();
    for (Map.Entry<String, MutableTotal> entry : byProduct.entrySet()) {
      String group;
      String category;
      ProductCategoryAssignment assignment = assignmentByProduct.get(entry.getKey());
      ProductCategory leaf =
          assignment == null || assignment.categoryId() == null
              ? null
              : categoryById.get(assignment.categoryId());
      if (leaf == null) {
        group = null;
        category = UNCATEGORISED;
      } else {
        group = leaf.groupName();
        category = leaf.name();
      }
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

  /**
   * Adds two possibly-null totals, treating null as zero. A product whose days are all unresolved
   * (all null) therefore surfaces as zero rather than throwing or dropping the row.
   */
  private static BigDecimal nullSafeAdd(BigDecimal current, BigDecimal add) {
    if (current == null) {
      return add == null ? BigDecimal.ZERO : add;
    }
    return add == null ? current : current.add(add);
  }

  @Override
  public List<FoodBevSplit> dailyFoodBev(LocalDate from, LocalDate to) {
    Map<String, ProductCategoryAssignment> assignmentByProduct = new HashMap<>();
    for (ProductCategoryAssignment assignment : assignments.findAll()) {
      assignmentByProduct.put(assignment.productNameKey(), assignment);
    }

    Map<UUID, ProductCategory> categoryById = new HashMap<>();
    for (ProductCategory category : categories.findAll()) {
      categoryById.put(category.id(), category);
    }

    // Sum each day's amount into food (group "Food") or bev (every other non-null group). A null
    // group (flat category, unassigned, or deleted category) and the "Uncategorised" label are
    // excluded from the split, mirroring salesByCategory's treatment of unclassified products.
    Map<LocalDate, MutableSplit> byDay = new LinkedHashMap<>();
    for (ProductSalesMetric metric : productMetrics.productSales(from, to)) {
      String group = groupFor(metric.productName(), assignmentByProduct, categoryById);
      if (group == null || UNCATEGORISED.equals(group)) {
        continue;
      }
      MutableSplit split = byDay.computeIfAbsent(metric.tradingDate(), d -> new MutableSplit());
      if (FOOD_GROUP.equals(group)) {
        split.food = nullSafeAdd(split.food, metric.amount());
      } else {
        split.bev = nullSafeAdd(split.bev, metric.amount());
      }
    }

    List<FoodBevSplit> out = new ArrayList<>(byDay.size());
    for (Map.Entry<LocalDate, MutableSplit> entry : byDay.entrySet()) {
      out.add(new FoodBevSplit(entry.getKey(), entry.getValue().food, entry.getValue().bev));
    }
    return out;
  }

  /**
   * The group of a product's leaf category, or null when the product is unassigned, its category
   * was deleted, or its category is flat (no parent group).
   */
  private static String groupFor(
      String productName,
      Map<String, ProductCategoryAssignment> assignmentByProduct,
      Map<UUID, ProductCategory> categoryById) {
    ProductCategoryAssignment assignment = assignmentByProduct.get(productName);
    ProductCategory leaf =
        assignment == null || assignment.categoryId() == null
            ? null
            : categoryById.get(assignment.categoryId());
    return leaf == null ? null : leaf.groupName();
  }

  /** Composite grouping key for a leaf category within its (nullable) group. */
  private record CategoryKey(String group, String category) {}

  private static final class MutableTotal {
    BigDecimal quantity;
    BigDecimal amount;
  }

  private static final class MutableSplit {
    BigDecimal food;
    BigDecimal bev;
  }
}
