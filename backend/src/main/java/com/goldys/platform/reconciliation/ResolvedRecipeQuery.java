package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.RecipeSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link RecipeMetricsQuery} backed by the resolved recipe projection. */
@Service
public class ResolvedRecipeQuery implements RecipeMetricsQuery {
  private static final int SCALE = 4;

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
    BigDecimal margin = subtract(r.menuPrice(), r.costPerPortion());
    return new RecipeCosting(
        r.recipeId(),
        r.recipeName(),
        r.recipeFolderName(),
        r.costPerPortion(),
        r.actualCost(),
        subtract(r.actualCost(), r.costPerPortion()),
        r.menuPrice(),
        margin,
        divide(margin, r.menuPrice()));
  }

  private static BigDecimal subtract(BigDecimal a, BigDecimal b) {
    return a == null || b == null ? null : a.subtract(b);
  }

  private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) {
      return null;
    }
    return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
  }
}
