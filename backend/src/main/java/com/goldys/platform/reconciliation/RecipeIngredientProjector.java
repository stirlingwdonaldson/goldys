package com.goldys.platform.reconciliation;

import com.goldys.platform.canonical.CanonicalRecipeIngredientQuery;
import com.goldys.platform.canonical.RecipeIngredientView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the recipe-ingredient read model ({@code resolved_recipe_ingredient}) from canonical
 * ingredient lines. Recipes are single-source (CTB), so there is no conflict resolution — the
 * projection is a straight pass-through of the current lines.
 */
@Service
public class RecipeIngredientProjector {
  private static final Clock CLOCK = Clock.systemUTC();

  private final CanonicalRecipeIngredientQuery canonical;
  private final ResolvedRecipeIngredientRepository resolved;

  public RecipeIngredientProjector(
      CanonicalRecipeIngredientQuery canonical, ResolvedRecipeIngredientRepository resolved) {
    this.canonical = canonical;
    this.resolved = resolved;
  }

  /** Rebuild the whole read model from canonical facts. */
  @Transactional
  public void recomputeAll() {
    resolved.deleteAllInBatch();
    Instant now = CLOCK.instant();
    List<ResolvedRecipeIngredient> rows =
        canonical.currentIngredients().stream().map(v -> toResolved(v, now)).toList();
    var evidence = canonical.evidence();
    rows.forEach(
        r ->
            r.sourceEvidence(
                evidence.get(r.parentRecipeId() + ":" + r.recipeDetailId() + ":" + r.stockId())));
    resolved.saveAll(rows);
  }

  private static ResolvedRecipeIngredient toResolved(RecipeIngredientView v, Instant now) {
    return new ResolvedRecipeIngredient(
        v.parentRecipeId(),
        v.lineRecipeId(),
        v.lineRecipeName(),
        v.isSubRecipe(),
        v.recipeDetailId(),
        v.stockId(),
        v.stockCode(),
        v.stockDescription(),
        v.portionWeight(),
        v.unitOfMeasurement(),
        v.costPerQty(),
        v.costPerUnit(),
        v.supplierName(),
        now,
        v.stockUomShortname(),
        v.stockUsable(),
        v.stockWastage(),
        v.isBrokenLink());
  }
}
