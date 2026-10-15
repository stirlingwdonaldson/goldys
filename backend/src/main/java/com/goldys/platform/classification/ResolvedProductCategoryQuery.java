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
    Classification classification = resolveClassification();

    // Emit a split for every date that has any resolved product-sales rows. A side with no rows in
    // its group that day is a real zero; a side with rows but a null (unresolved) amount is null,
    // so GrainAggregator can flag it UNRESOLVED rather than silently zeroing an unresolved day.
    Map<LocalDate, MutableSplit> byDay = new LinkedHashMap<>();
    for (ProductSalesMetric metric : productMetrics.productSales(from, to)) {
      MutableSplit split = byDay.computeIfAbsent(metric.tradingDate(), d -> new MutableSplit());
      String group = classification.groupForProduct(metric.productName());
      if (group == null || UNCATEGORISED.equals(group)) {
        continue;
      }
      boolean food = FOOD_GROUP.equals(group);
      if (metric.amount() == null) {
        if (food) {
          split.foodUnresolved = true;
        } else {
          split.bevUnresolved = true;
        }
      } else if (food) {
        split.food = split.food.add(metric.amount());
      } else {
        split.bev = split.bev.add(metric.amount());
      }
    }

    List<FoodBevSplit> out = new ArrayList<>(byDay.size());
    for (Map.Entry<LocalDate, MutableSplit> entry : byDay.entrySet()) {
      MutableSplit s = entry.getValue();
      out.add(
          new FoodBevSplit(
              entry.getKey(), s.foodUnresolved ? null : s.food, s.bevUnresolved ? null : s.bev));
    }
    return out;
  }

  /**
   * Builds the product → category/group lookup once per query, so {@link #salesByCategory} and
   * {@link #dailyFoodBev} share the same assignment/category maps rather than rebuilding them.
   */
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

  /** The resolved product → category/group mapping for one query. */
  private record Classification(
      Map<String, ProductCategoryAssignment> assignmentByProduct,
      Map<UUID, ProductCategory> categoryById) {

    /** The leaf category of a product, or null when unassigned or its category was deleted. */
    ProductCategory leafFor(String productName) {
      ProductCategoryAssignment assignment = assignmentByProduct.get(productName);
      return assignment == null || assignment.categoryId() == null
          ? null
          : categoryById.get(assignment.categoryId());
    }

    /** The group of a product, or null when unassigned, deleted, or flat (no parent group). */
    String groupForProduct(String productName) {
      ProductCategory leaf = leafFor(productName);
      return leaf == null ? null : leaf.groupName();
    }
  }

  /** Composite grouping key for a leaf category within its (nullable) group. */
  private record CategoryKey(String group, String category) {}

  private static final class MutableTotal {
    BigDecimal quantity;
    BigDecimal amount;
  }

  private static final class MutableSplit {
    BigDecimal food = BigDecimal.ZERO;
    BigDecimal bev = BigDecimal.ZERO;
    boolean foodUnresolved;
    boolean bevUnresolved;
  }
}
