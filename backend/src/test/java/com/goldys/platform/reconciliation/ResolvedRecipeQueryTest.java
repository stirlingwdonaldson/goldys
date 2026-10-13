package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolvedRecipeQueryTest {

  private final ResolvedRecipeRepository repository = mock(ResolvedRecipeRepository.class);
  private final ResolvedRecipeQuery query = new ResolvedRecipeQuery(repository);

  @Test
  void recipesReturnsResolvedRows() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", "Mains", "6.00", "18.50", "7.20")));

    List<RecipeSummary> out = query.recipes();

    assertThat(out).hasSize(1);
    RecipeSummary s = out.get(0);
    assertThat(s.recipeId()).isEqualTo(1);
    assertThat(s.name()).isEqualTo("Parma");
    assertThat(s.folder()).isEqualTo("Mains");
    assertThat(s.costPerPortion()).isEqualByComparingTo("6.00");
    assertThat(s.menuPrice()).isEqualByComparingTo("18.50");
    assertThat(s.actualCost()).isEqualByComparingTo("7.20");
  }

  @Test
  void costingComputesMarginGpCostGap() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", "Mains", "6.00", "18.50", "7.20")));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isEqualByComparingTo("12.50");
    assertThat(c.gpPercent()).isEqualByComparingTo("0.6757");
    assertThat(c.costGap()).isEqualByComparingTo("1.20");
  }

  @Test
  void costingNullWhenPriceOrCostNull() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", null, null, null, null)));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isNull();
    assertThat(c.gpPercent()).isNull();
    assertThat(c.costGap()).isNull();
  }

  @Test
  void costingNullGpWhenMenuPriceZero() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", null, "6.00", "0.00", "7.20")));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isEqualByComparingTo("-6.00");
    assertThat(c.gpPercent()).isNull();
  }

  @Test
  void costingNullWhenCostPerPortionZero() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", null, "0.00", "18.50", "7.20")));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isNull();
    assertThat(c.gpPercent()).isNull();
    assertThat(c.costGap()).isNull();
  }

  @Test
  void costingCarriesYield() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(List.of(recipe(1, "Parma", "Mains", "6.00", "18.50", "7.20", "1 serve")));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.portionSize()).isEqualTo("1 serve");
  }

  private static ResolvedRecipe recipe(
      int id, String name, String folder, String cost, String price, String actualCost) {
    return recipe(id, name, folder, cost, price, actualCost, null);
  }

  private static ResolvedRecipe recipe(
      int id,
      String name,
      String folder,
      String cost,
      String price,
      String actualCost,
      String portionSize) {
    return new ResolvedRecipe(
        id,
        name,
        folder,
        cost == null ? null : new BigDecimal(cost),
        null,
        null,
        portionSize,
        null,
        price == null ? null : new BigDecimal(price),
        actualCost == null ? null : new BigDecimal(actualCost),
        "ON_MENU",
        price == null ? "NONE" : "CURRENT",
        null,
        null,
        null,
        "SINGLE_SOURCE",
        "CTB",
        Instant.now());
  }
}
