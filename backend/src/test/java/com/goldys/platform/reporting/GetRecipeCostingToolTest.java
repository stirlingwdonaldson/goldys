package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.widget.TableWidgetSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GetRecipeCostingToolTest {

  private final RecipeMetricsQuery metrics = mock(RecipeMetricsQuery.class);
  private final GetRecipeCostingTool tool = new GetRecipeCostingTool(metrics);

  @Test
  void returnsTableWidget() {
    when(metrics.costing()).thenReturn(List.of(recipe()));

    ToolResult result = tool.execute(new GetRecipeCostingInput(0), null);

    assertThat(result.widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec widget = (TableWidgetSpec) result.widget();
    assertThat(widget.columns())
        .extracting(c -> c.key())
        .containsExactly(
            "recipe",
            "folder",
            "cost_per_portion",
            "actual_cost",
            "cost_gap",
            "menu_price",
            "margin",
            "gp_percent");
    assertThat(widget.rows()).hasSize(1);
    Map<String, Object> row = widget.rows().get(0);
    assertThat(row.get("recipe")).isEqualTo("Parma");
    assertThat(row.get("margin")).isEqualTo(new BigDecimal("12.50"));
  }

  @Test
  void appliesLimit() {
    when(metrics.costing()).thenReturn(List.of(recipe(), recipe()));

    ToolResult result = tool.execute(new GetRecipeCostingInput(1), null);

    assertThat(((TableWidgetSpec) result.widget()).rows()).hasSize(1);
  }

  @Test
  void resourceIsRecipesCost() {
    assertThat(tool.resource().value()).isEqualTo("recipes.cost");
  }

  @Test
  void toMetricQueriesIsEmpty() {
    assertThat(tool.toMetricQueries(new GetRecipeCostingInput(0))).isEmpty();
  }

  private static RecipeCosting recipe() {
    return new RecipeCosting(
        1,
        "Parma",
        "Mains",
        new BigDecimal("6.00"),
        new BigDecimal("7.20"),
        new BigDecimal("1.20"),
        new BigDecimal("18.50"),
        new BigDecimal("12.50"),
        new BigDecimal("0.6757"));
  }
}
