package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.RecipeIngredient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolvedRecipeIngredientQueryTest {

  private final ResolvedRecipeIngredientRepository repository =
      mock(ResolvedRecipeIngredientRepository.class);
  private final ResolvedRecipeIngredientQuery query = new ResolvedRecipeIngredientQuery(repository);

  @Test
  void ingredientsForReturnsResolvedRows() {
    when(repository.findByParentRecipeId(463))
        .thenReturn(
            List.of(
                row(463, 463, "Cheese Burger", false, 101, 42, "BUN", "Brioche bun", "1.0000"),
                row(463, 900, "Aioli", true, 201, 7, "GARLIC", "Garlic", "0.0500")));

    List<RecipeIngredient> out = query.ingredientsFor(463);

    assertThat(out).hasSize(2);
    RecipeIngredient main = out.get(0);
    assertThat(main.parentRecipeId()).isEqualTo(463);
    assertThat(main.lineRecipeName()).isEqualTo("Cheese Burger");
    assertThat(main.isSubRecipe()).isFalse();
    assertThat(main.stockDescription()).isEqualTo("Brioche bun");
    assertThat(main.portionWeight()).isEqualByComparingTo("1.0000");
    assertThat(main.costPerQty()).isEqualByComparingTo("6.00");

    RecipeIngredient sub = out.get(1);
    assertThat(sub.lineRecipeId()).isEqualTo(900);
    assertThat(sub.isSubRecipe()).isTrue();
  }

  private static ResolvedRecipeIngredient row(
      int parentRecipeId,
      int lineRecipeId,
      String lineRecipeName,
      boolean isSubRecipe,
      int recipeDetailId,
      int stockId,
      String stockCode,
      String stockDescription,
      String portionWeight) {
    return new ResolvedRecipeIngredient(
        parentRecipeId,
        lineRecipeId,
        lineRecipeName,
        isSubRecipe,
        recipeDetailId,
        stockId,
        stockCode,
        stockDescription,
        new BigDecimal(portionWeight),
        "grams",
        new BigDecimal("6.00"),
        new BigDecimal("2.00"),
        "Acme",
        Instant.now());
  }
}
