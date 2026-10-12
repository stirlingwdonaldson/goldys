package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GetRecipeIngredientsToolTest {

  private final RecipeIngredientMetricsQuery metrics = mock(RecipeIngredientMetricsQuery.class);
  private final GetRecipeIngredientsTool tool = new GetRecipeIngredientsTool(metrics);

  @Test
  void returnsTableWidget() {
    when(metrics.ingredientsFor(463)).thenReturn(List.of(line()));

    ToolResult result = tool.execute(new GetRecipeIngredientsInput(463), null);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.columns())
        .extracting(c -> c.key())
        .containsExactly(
            "recipe", "ingredient", "qty", "unit", "cost", "supplier", "is_sub_recipe");
    assertThat(widget.rows()).hasSize(1);
    Map<String, Object> row = widget.rows().get(0);
    assertThat(row.get("recipe")).isEqualTo("Cheese Burger");
    assertThat(row.get("ingredient")).isEqualTo("Brioche bun");
    assertThat(row.get("qty")).isEqualTo(new BigDecimal("1.0000"));
    assertThat(row.get("cost")).isEqualTo(new BigDecimal("6.00"));
    assertThat(row.get("supplier")).isEqualTo("Acme");
    assertThat(row.get("is_sub_recipe")).isEqualTo(false);
  }

  @Test
  void appliesLimit() {
    when(metrics.ingredientsFor(463)).thenReturn(List.of(line(), line()));

    ToolResult result = tool.execute(new GetRecipeIngredientsInput(463, 1), null);

    assertThat(((TableWidgetSpec) result.widget()).rows()).hasSize(1);
  }

  @Test
  void resourceIsRecipesIngredients() {
    assertThat(tool.resource().value()).isEqualTo("recipes.ingredients");
  }

  @Test
  void toMetricQueriesIsEmpty() {
    assertThat(tool.toMetricQueries(new GetRecipeIngredientsInput(463))).isEmpty();
  }

  @Test
  void rejectsNonPositiveRecipeId() {
    assertThatThrownBy(() -> new GetRecipeIngredientsInput(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RecipeIngredient line() {
    return new RecipeIngredient(
        463,
        463,
        "Cheese Burger",
        false,
        101,
        42,
        "BUN",
        "Brioche bun",
        new BigDecimal("1.0000"),
        "grams",
        new BigDecimal("6.00"),
        new BigDecimal("2.00"),
        "Acme");
  }
}
