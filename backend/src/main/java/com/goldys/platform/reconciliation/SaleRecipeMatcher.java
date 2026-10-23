package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.semantic.RecipeCosting;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit links win; only an unassigned item can fall back to a unique exact recipe name. */
final class SaleRecipeMatcher {
  record Link(String description, int recipeId, Boolean active) {}

  record Match(RecipeCosting recipe, String method, String status) {}

  private final Map<Integer, RecipeCosting> byId;
  private final Map<String, List<RecipeCosting>> byName;
  private final Map<String, List<Link>> links;

  SaleRecipeMatcher(List<RecipeCosting> recipes, List<Link> links) {
    byId = recipes.stream().collect(Collectors.toMap(RecipeCosting::recipeId, Function.identity()));
    byName =
        recipes.stream().collect(Collectors.groupingBy(r -> ProductNameKey.normalize(r.name())));
    this.links =
        links.stream()
            .collect(Collectors.groupingBy(l -> ProductNameKey.normalize(l.description())));
  }

  Match match(String name) {
    String key = ProductNameKey.normalize(name);
    if (key.isBlank()) return new Match(null, null, "UNMATCHED");
    List<Link> assigned =
        links.getOrDefault(key, List.of()).stream().filter(l -> l.recipeId() > 0).toList();
    if (!assigned.isEmpty()) {
      var ids = assigned.stream().map(Link::recipeId).distinct().toList();
      if (ids.size() != 1 || assigned.stream().anyMatch(l -> Boolean.FALSE.equals(l.active())))
        return new Match(null, null, "AMBIGUOUS_OR_INACTIVE_LINK");
      RecipeCosting recipe = byId.get(ids.getFirst());
      return new Match(recipe, "CTB_LINK", recipe == null ? "RECIPE_MISSING" : "MATCHED");
    }
    List<RecipeCosting> candidates = byName.getOrDefault(key, List.of());
    if (candidates.size() != 1)
      return new Match(null, null, candidates.isEmpty() ? "UNMATCHED" : "AMBIGUOUS_NAME");
    return new Match(candidates.getFirst(), "EXACT_RECIPE_NAME", "MATCHED");
  }
}
