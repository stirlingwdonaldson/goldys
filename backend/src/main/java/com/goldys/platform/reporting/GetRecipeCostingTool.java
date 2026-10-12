package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.RecipeCosting;
import com.goldys.platform.semantic.RecipeMetricsQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Recipe costing for Ask Goldy's: cost per portion, actual cost, cost gap, menu price, margin and
 * gross-profit percent per dish, rendered as a table. Reads {@link RecipeMetricsQuery} directly
 * (the recipe master data is not a time-series catalogue metric), so {@code toMetricQueries} is
 * empty and the widget is not re-runnable from a saved dashboard.
 */
@Component
public class GetRecipeCostingTool implements ReportingTool {
  private final RecipeMetricsQuery metrics;

  public GetRecipeCostingTool(RecipeMetricsQuery metrics) {
    this.metrics = metrics;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_RECIPE_COSTING;
  }

  @Override
  public String name() {
    return "get_recipe_costing";
  }

  @Override
  public String description() {
    return "Per-dish cost, cost gap, menu price, margin and gross-profit percent.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetRecipeCostingInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("recipes.cost");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    GetRecipeCostingInput in = require(input);
    List<RecipeCosting> rows = metrics.costing();
    if (in.limit() > 0 && rows.size() > in.limit()) {
      rows = rows.subList(0, in.limit());
    }
    List<Column> columns =
        List.of(
            new Column("recipe", "Recipe", null),
            new Column("folder", "Folder", null),
            new Column("cost_per_portion", "Cost/portion", "currency"),
            new Column("actual_cost", "Actual cost", "currency"),
            new Column("cost_gap", "Cost gap", "currency"),
            new Column("menu_price", "Menu price", "currency"),
            new Column("margin", "Margin", "currency"),
            new Column("gp_percent", "GP%", "percent"));
    List<Map<String, Object>> tableRows =
        rows.stream()
            .map(
                r -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("recipe", r.name());
                  row.put("folder", r.folder());
                  row.put("cost_per_portion", r.costPerPortion());
                  row.put("actual_cost", r.actualCost());
                  row.put("cost_gap", r.costGap());
                  row.put("menu_price", r.menuPrice());
                  row.put("margin", r.margin());
                  row.put("gp_percent", r.gpPercent());
                  return row;
                })
            .toList();
    WidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(), "Recipe costing", null, columns, tableRows, null);
    return new ToolResult(widget, List.of(), List.of(), List.of());
  }

  private static GetRecipeCostingInput require(ToolInput input) {
    if (!(input instanceof GetRecipeCostingInput in)) {
      throw new IllegalArgumentException(
          "Expected GetRecipeCostingInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }
}
