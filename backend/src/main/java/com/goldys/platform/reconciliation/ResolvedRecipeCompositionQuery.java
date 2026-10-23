package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolvedRecipeCompositionQuery implements RecipeCompositionQuery {
  private final ResolvedRecipeDefinitionRepository definitions;
  private final ResolvedRecipeCatalogRepository catalogs;

  private final Duration maxAge;
  private final BigDecimal tolerance;

  public ResolvedRecipeCompositionQuery(
      ResolvedRecipeDefinitionRepository definitions,
      ResolvedRecipeCatalogRepository catalogs,
      @Value("${ctb.recipes.max-age:PT36H}") Duration maxAge,
      @Value("${ctb.recipes.cost-tolerance:0.01}") BigDecimal tolerance) {
    if (maxAge.isNegative() || maxAge.isZero() || tolerance.signum() < 0)
      throw new IllegalArgumentException("Invalid recipe reporting limits");
    this.definitions = definitions;
    this.catalogs = catalogs;
    this.maxAge = maxAge;
    this.tolerance = tolerance;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RecipeComposition> composition(int recipeId) {
    // Bound graph loading to the requested recipe and its descendants, using stable source ids.
    Map<Integer, RecipeDefinition> graph = new LinkedHashMap<>();
    Set<Integer> visited = new HashSet<>();
    Deque<Integer> pending = new ArrayDeque<>();
    pending.add(recipeId);
    while (!pending.isEmpty() && visited.size() < 10000) {
      int id = pending.removeFirst();
      if (!visited.add(id)) continue;
      definitions
          .findById(id)
          .ifPresent(
              row -> {
                RecipeDefinition value = row.definition();
                graph.put(id, value);
                value.usages().forEach(usage -> pending.add(usage.recipeId()));
              });
    }
    RecipeDefinition root = graph.get(recipeId);
    return root == null
        ? Optional.empty()
        : Optional.of(
            new RecipeCompositionCalculator(graph, Instant.now(), maxAge, tolerance)
                .calculate(root, catalog().orElse(null)));
  }

  @org.springframework.beans.factory.annotation.Autowired
  private ResolvedRecipeDetailsQuery details;

  @Override
  public Optional<RecipeDetails> details(int recipeId) {
    return details.details(recipeId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RecipeCatalog> catalog() {
    return catalogs.findById(1).map(ResolvedRecipeCatalog::catalog);
  }
}
