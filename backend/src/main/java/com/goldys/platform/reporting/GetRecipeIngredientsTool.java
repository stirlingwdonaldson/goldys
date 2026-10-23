package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.RecipeIngredient;
import com.goldys.platform.semantic.RecipeIngredientMetricsQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-recipe ingredient composition for Ask Goldy's: each line's ingredient, quantity, unit, CTB
 * cost and supplier, plus the sub-recipe flag. Reads {@link RecipeIngredientMetricsQuery} directly
 * (recipe ingredient lines are master data, not a time-series catalogue metric), so {@code
 * toMetricQueries} is empty and the widget is not re-runnable from a saved dashboard.
 */
@Component
public class GetRecipeIngredientsTool implements ReportingTool {
  private final RecipeIngredientMetricsQuery metrics;

  public GetRecipeIngredientsTool(RecipeIngredientMetricsQuery metrics) {
    this.metrics = metrics;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_RECIPE_INGREDIENTS;
  }

  @Override
  public String name() {
    return "get_recipe_ingredients";
  }

  @Override
  public String description() {
    return "The ingredient lines of one recipe: ingredient, quantity, unit, cost and supplier, "
        + "including explicit sub-recipe usage where received. Use for 'what goes into this dish' questions, not for a "
        + "metric over a period.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetRecipeIngredientsInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("recipes.ingredients");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    GetRecipeIngredientsInput in = require(input);
    List<RecipeIngredient> rows = metrics.ingredientsFor(in.recipeId());
    if (in.limit() > 0 && rows.size() > in.limit()) {
      rows = rows.subList(0, in.limit());
    }
    List<Column> columns =
        List.of(
            new Column("recipe", "Recipe", null),
            new Column("ingredient", "Ingredient", null),
            new Column("qty", "Qty", null),
            new Column("unit", "Unit", null),
            new Column("cost", "Cost", "currency"),
            new Column("supplier", "Supplier", null),
            new Column("stock_unit", "Source stock unit", null),
            new Column("usable", "Source usable quantity", null),
            new Column("wastage", "Source wastage", null),
            new Column("broken_link", "Broken link", null),
            new Column("is_sub_recipe", "Sub-recipe", null));
    List<Map<String, Object>> tableRows =
        rows.stream()
            .map(
                r -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("recipe", r.lineRecipeName());
                  row.put("ingredient", r.stockDescription());
                  row.put("qty", r.portionWeight());
                  row.put("unit", r.unitOfMeasurement());
                  row.put("cost", r.costPerQty());
                  row.put("supplier", r.supplierName());
                  row.put("is_sub_recipe", r.isSubRecipe());
                  row.put("stock_unit", r.stockUomShortname());
                  row.put("usable", r.stockUsable());
                  row.put("wastage", r.stockWastage());
                  row.put("broken_link", r.isBrokenLink());
                  return row;
                })
            .toList();
    WidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Recipe ingredients", null, columns, tableRows, null);
    return new ToolResult(
        widget,
        List.of(
            "Quantities and costs retain signed adjustments. Complete refreshes show direct sub-recipe usage; older flattened records may show unscaled child quantities. CTB costs are source estimates."),
        List.of(),
        List.of());
  }

  private static GetRecipeIngredientsInput require(ToolInput input) {
    if (!(input instanceof GetRecipeIngredientsInput in)) {
      throw new IllegalArgumentException(
          "Expected GetRecipeIngredientsInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }
}
