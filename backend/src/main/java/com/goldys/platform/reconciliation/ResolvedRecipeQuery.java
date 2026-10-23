package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.semantic.RecipeSummary;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link RecipeMetricsQuery} backed by the resolved recipe projection. */
@Service
public class ResolvedRecipeQuery implements RecipeMetricsQuery {

  private final ResolvedRecipeRepository repository;
  private final RecipeDefinitionReadAdapter definitions;

  @Autowired
  public ResolvedRecipeQuery(
      ResolvedRecipeRepository repository, RecipeDefinitionReadAdapter definitions) {
    this.repository = repository;
    this.definitions = definitions;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RecipeSummary> recipes() {
    return currentRows().stream().map(ResolvedRecipeQuery::toSummary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<RecipeCosting> costing() {
    return currentRows().stream()
        .map(r -> definitions == null ? toCosting(r) : definitions.status(toCosting(r)))
        .toList();
  }

  private List<ResolvedRecipe> currentRows() {
    List<ResolvedRecipe> rows = repository.findAllByOrderByRecipeNameAsc();
    return definitions == null ? rows : definitions.overlay(rows);
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
        r.lastSoldDate(),
        r.costEvidence().stream()
            .map(com.goldys.platform.semantic.RecipeEvidence::capturedAt)
            .max(java.time.Instant::compareTo)
            .orElse(null),
        "CTB",
        r.costPerPortion() == null ? "DETAILS_MISSING" : "LEGACY_OR_COMPLETE",
        false);
  }
}
