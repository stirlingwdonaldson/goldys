package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.RecipeSummary;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link RecipeMetricsQuery} backed by the resolved recipe projection. */
@Service
public class ResolvedRecipeQuery implements RecipeMetricsQuery {

  private final ResolvedRecipeRepository repository;

  public ResolvedRecipeQuery(ResolvedRecipeRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<RecipeSummary> recipes() {
    return repository.findAllByOrderByRecipeNameAsc().stream()
        .map(ResolvedRecipeQuery::toSummary)
        .toList();
  }

  @Override
  public List<RecipeCosting> costing() {
    return repository.findAllByOrderByRecipeNameAsc().stream()
        .map(ResolvedRecipeQuery::toCosting)
        .toList();
  }

  private static RecipeSummary toSummary(ResolvedRecipe r) {
    return new RecipeSummary(
        r.recipeId(),
        r.recipeName(),
        r.recipeFolderName(),
        r.costPerPortion(),
        r.costPerPortionInclLabour(),
        r.totalCost(),
        r.portionSize(),
        r.portionSizeValue(),
        r.menuPrice(),
        r.actualCost());
  }

  private static RecipeCosting toCosting(ResolvedRecipe r) {
    // Known zero cost remains known; missing cost remains null.
    BigDecimal cost = r.costPerPortion();
    BigDecimal margin = null; // Comparable tax/effective cost basis has not been verified.
    return new RecipeCosting(
        r.recipeId(),
        r.recipeName(),
        r.recipeFolderName(),
        r.portionSize(),
        cost,
        r.actualCost(),
        null, // Incomparable source tax/effective bases cannot establish a cost gap.
        r.menuPrice(),
        margin,
        null,
        r.menuState(),
        r.priceSource(),
        r.lastSoldPrice(),
        r.lastSoldCost(),
        r.lastSoldDate());
  }
}
