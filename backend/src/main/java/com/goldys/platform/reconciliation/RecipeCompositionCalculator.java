package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Cost estimates over resolved observations. CTB-reported figures are never rewritten. */
final class RecipeCompositionCalculator {
  private static final MathContext MATH = MathContext.DECIMAL128;
  private final Map<Integer, RecipeDefinition> definitions;
  private final Instant now;
  private final Duration maxAge;
  private final BigDecimal tolerance;
  private final Map<Integer, BigDecimal> cachedTotals = new HashMap<>();

  RecipeCompositionCalculator(
      Map<Integer, RecipeDefinition> definitions,
      Instant now,
      Duration maxAge,
      BigDecimal tolerance) {
    this.definitions = definitions;
    this.now = now;
    this.maxAge = maxAge;
    this.tolerance = tolerance;
  }

  RecipeComposition calculate(RecipeDefinition definition, RecipeCatalog catalog) {
    Set<String> issues = new LinkedHashSet<>();
    BigDecimal sourceTotal = sourceTotal(definition, issues);
    BigDecimal total = total(definition, new LinkedHashSet<>(), issues);
    BigDecimal difference =
        total == null || definition.header().totalCost() == null
            ? null
            : total.subtract(definition.header().totalCost(), MATH);
    if (difference != null && difference.abs().compareTo(tolerance) > 0)
      issues.add("COST_DISCREPANCY");
    List<String> path = folderPath(definition.header().folderId(), catalog, issues);
    List<RecipeOutletAllocation> outlets =
        catalog == null
            ? List.of()
            : catalog.allocations().stream()
                .filter(
                    a ->
                        a.entityType().equals("recipe")
                            && a.entityId() == definition.header().recipeId())
                .toList();
    if (catalog == null) issues.add("CATALOG_NOT_RECEIVED");
    else if (old(catalog.observedAt())) issues.add("STALE_CATALOG");
    return new RecipeComposition(
        definition,
        path,
        outlets,
        sourceTotal,
        total,
        difference,
        List.copyOf(issues),
        old(definition.observedAt()));
  }

  private BigDecimal sourceTotal(RecipeDefinition definition, Set<String> issues) {
    BigDecimal sum = BigDecimal.ZERO;
    for (RecipeStock stock : definition.stocks()) {
      if (stock.cost() == null) {
        issues.add("MISSING_STOCK_COST");
        return null;
      }
      sum = sum.add(stock.cost(), MATH);
    }
    for (RecipeUsage usage : definition.usages()) {
      if (usage.cost() == null) {
        issues.add("MISSING_USAGE_COST");
        return null;
      }
      sum = sum.add(usage.cost(), MATH);
    }
    return sum;
  }

  private BigDecimal total(RecipeDefinition definition, Set<Integer> path, Set<String> issues) {
    int id = definition.header().recipeId();
    if (path.size() >= 64 || !path.add(id)) {
      issues.add("RECIPE_CYCLE_OR_DEPTH_LIMIT");
      return null;
    }
    try {
      if (cachedTotals.containsKey(id)) return cachedTotals.get(id);
      if (old(definition.observedAt())) issues.add("STALE_RECIPE:" + id);
      BigDecimal sum = BigDecimal.ZERO;
      boolean complete = true;
      for (RecipeStock stock : definition.stocks()) {
        if (!Boolean.FALSE.equals(stock.brokenLink())) {
          issues.add("UNVERIFIED_STOCK_LINK:" + id);
          complete = false;
        }
        if (stock.cost() == null) {
          issues.add("MISSING_STOCK_COST:" + id);
          complete = false;
        } else sum = sum.add(stock.cost(), MATH);
      }
      for (RecipeUsage usage : definition.usages()) {
        BigDecimal contribution = contribution(usage, path, issues);
        if (contribution == null) complete = false;
        else sum = sum.add(contribution, MATH);
      }
      if (complete) cachedTotals.put(id, sum);
      return complete ? sum : null;
    } finally {
      path.remove(id);
    }
  }

  private BigDecimal contribution(RecipeUsage usage, Set<Integer> path, Set<String> issues) {
    RecipeDefinition child = definitions.get(usage.recipeId());
    if (child == null) {
      issues.add("MISSING_SUB_RECIPE:" + usage.recipeId());
      return null;
    }
    RecipeHeader header = child.header();
    if (!Boolean.FALSE.equals(usage.brokenLink())
        || usage.convertedQuantity() == null
        || !positive(usage.originalPortionSize())
        || !positive(header.portionCount())
        || header.portionSize() == null
        || header.unitId() == null
        || usage.originalUnitId() == null
        || !Objects.equals(header.unitId(), usage.originalUnitId())
        || header.portionSize().compareTo(usage.originalPortionSize()) != 0) {
      issues.add("INCOMPATIBLE_SUB_RECIPE_YIELD:" + usage.recipeId());
      return null;
    }
    BigDecimal childTotal = total(child, path, issues);
    if (childTotal == null) return null;
    // CTB's originalPortionSize describes one portion's yield, while noOfPortion describes how
    // many such portions the child batch makes. Preserve both denominators and signed usage.
    BigDecimal contribution =
        childTotal
            .divide(header.portionCount(), MATH)
            .multiply(usage.convertedQuantity(), MATH)
            .divide(usage.originalPortionSize(), MATH);
    if (usage.cost() != null && contribution.subtract(usage.cost()).abs().compareTo(tolerance) > 0)
      issues.add("SUB_RECIPE_COST_DISCREPANCY:" + usage.recipeId());
    return contribution;
  }

  private List<String> folderPath(Integer folderId, RecipeCatalog catalog, Set<String> issues) {
    if (folderId == null || catalog == null) return List.of();
    Map<Integer, RecipeFolder> folders = new HashMap<>();
    catalog.folders().forEach(f -> folders.put(f.folderId(), f));
    List<String> names = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    Integer cursor = folderId;
    while (cursor != null) {
      if (!visited.add(cursor)) {
        issues.add("FOLDER_CYCLE");
        break;
      }
      RecipeFolder folder = folders.get(cursor);
      if (folder == null) {
        issues.add("MISSING_FOLDER:" + cursor);
        break;
      }
      if (folder.name() != null) names.add(folder.name());
      cursor = folder.parentId();
    }
    Collections.reverse(names);
    return List.copyOf(names);
  }

  private boolean old(Instant at) {
    return at.isBefore(now.minus(maxAge));
  }

  private static boolean positive(BigDecimal n) {
    return n != null && n.signum() > 0;
  }
}
