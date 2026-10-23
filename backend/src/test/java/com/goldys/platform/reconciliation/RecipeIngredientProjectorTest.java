package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalRecipeIngredientQuery;
import com.goldys.platform.canonical.RecipeIngredientView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecipeIngredientProjectorTest {

  private final CanonicalRecipeIngredientQuery canonical =
      mock(CanonicalRecipeIngredientQuery.class);
  private final ResolvedRecipeIngredientRepository resolved =
      mock(ResolvedRecipeIngredientRepository.class);
  private final RecipeIngredientProjector projector =
      new RecipeIngredientProjector(canonical, resolved);

  @Test
  void projectsIngredientLines() {
    when(canonical.currentIngredients())
        .thenReturn(
            List.of(
                view(463, 463, "Cheese Burger", false, 101, 42, "BUN", "Brioche bun", "1.0000"),
                view(463, 900, "Aioli", true, 201, 7, "GARLIC", "Garlic", "0.0500")));

    projector.recomputeAll();

    List<ResolvedRecipeIngredient> rows = captured();
    assertThat(rows).hasSize(2);
    ResolvedRecipeIngredient main = rows.get(0);
    assertThat(main.parentRecipeId()).isEqualTo(463);
    assertThat(main.lineRecipeId()).isEqualTo(463);
    assertThat(main.isSubRecipe()).isFalse();
    assertThat(main.stockDescription()).isEqualTo("Brioche bun");
    assertThat(main.portionWeight()).isEqualByComparingTo("1.0000");
    assertThat(main.stockUomShortname()).isEqualTo("kg");
    assertThat(main.stockUsable()).isEqualByComparingTo("0");
    assertThat(main.stockWastage()).isNull();
    assertThat(main.isBrokenLink()).isTrue();

    ResolvedRecipeIngredient sub = rows.get(1);
    assertThat(sub.lineRecipeId()).isEqualTo(900);
    assertThat(sub.lineRecipeName()).isEqualTo("Aioli");
    assertThat(sub.isSubRecipe()).isTrue();
  }

  @Test
  void recomputeAllRebuildsCleanly() {
    when(canonical.currentIngredients()).thenReturn(List.of());

    projector.recomputeAll();
    projector.recomputeAll();

    verify(resolved, times(2)).deleteAllInBatch();
  }

  @SuppressWarnings("unchecked")
  private List<ResolvedRecipeIngredient> captured() {
    ArgumentCaptor<List<ResolvedRecipeIngredient>> captor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(resolved).saveAll(captor.capture());
    return captor.getValue();
  }

  private static RecipeIngredientView view(
      int parentRecipeId,
      int lineRecipeId,
      String lineRecipeName,
      boolean isSubRecipe,
      int recipeDetailId,
      int stockId,
      String stockCode,
      String stockDescription,
      String portionWeight) {
    return new RecipeIngredientView(
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
        "kg",
        BigDecimal.ZERO,
        null,
        true);
  }
}
