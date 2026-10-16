package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    assertThat(c.margin()).isNull();
    assertThat(c.gpPercent()).isNull();
    assertThat(c.costGap()).isNull();
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

    assertThat(c.margin()).isNull();
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
    assertThat(c.costPerPortion()).isEqualByComparingTo("0");
  }

  @Test
  void costingUsesLastSoldPriceWhenNoCurrent() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(
            List.of(
                recipe(
                    1,
                    "Parma",
                    "Mains",
                    "6.00",
                    null,
                    null,
                    null,
                    "OFF_MENU",
                    "NONE",
                    "18.50",
                    "7.20",
                    LocalDate.of(2026, 9, 14))));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isNull();
    assertThat(c.gpPercent()).isNull();
    assertThat(c.costGap()).isNull();
  }

  @Test
  void costingNullWhenNoEffectivePrice() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(
            List.of(
                recipe(
                    1,
                    "Parma",
                    "Mains",
                    null,
                    null,
                    null,
                    null,
                    "OFF_MENU",
                    "NONE",
                    null,
                    null,
                    null)));

    RecipeCosting c = query.costing().get(0);

    assertThat(c.margin()).isNull();
    assertThat(c.gpPercent()).isNull();
    assertThat(c.costGap()).isNull();
  }

  @Test
  void costingNullGpWhenEffectivePriceZero() {
    when(repository.findAllByOrderByRecipeNameAsc())
        .thenReturn(
            List.of(
                recipe(
                    1,
                    "Parma",
                    "Mains",
                    "6.00",
                    null,
                    null,
                    null,
                    "OFF_MENU",
                    "NONE",
                    "0.00",
                    null,
                    null)));

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
    return recipe(
        id,
        name,
        folder,
        cost,
        price,
        actualCost,
        portionSize,
        "ON_MENU",
        price == null ? "NONE" : "CURRENT",
        null,
        null,
        null);
  }

  private static ResolvedRecipe recipe(
      int id,
      String name,
      String folder,
      String cost,
      String price,
      String actualCost,
      String portionSize,
      String menuState,
      String priceSource,
      String lastSoldPrice,
      String lastSoldCost,
      LocalDate lastSoldDate) {
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
        menuState,
        priceSource,
        lastSoldPrice == null ? null : new BigDecimal(lastSoldPrice),
        lastSoldCost == null ? null : new BigDecimal(lastSoldCost),
        lastSoldDate,
        "SINGLE_SOURCE",
        "CTB",
        Instant.now());
  }
}
