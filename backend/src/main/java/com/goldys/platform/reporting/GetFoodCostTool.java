package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.InventoryMetricsQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved food cost (COGS) and wastage for a date range, emitted as a table widget. */
@Component
public class GetFoodCostTool implements ReportingTool {
  private final InventoryMetricsQuery inventory;

  public GetFoodCostTool(InventoryMetricsQuery inventory) {
    this.inventory = inventory;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_FOOD_COST;
  }

  @Override
  public String name() {
    return "get_food_cost";
  }

  @Override
  public String description() {
    return "Resolved food cost (COGS from purchases) and wastage for a date range.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetFoodCostInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("inventory.cost");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetFoodCostInput in)) {
      throw new IllegalArgumentException(
          "Expected GetFoodCostInput, got " + input.getClass().getSimpleName());
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("purchases", inventory.purchases(in.startDate(), in.endDate()));
    row.put("wastage", inventory.wastage(in.startDate(), in.endDate()));

    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Food cost",
            "Resolved food cost for " + in.startDate() + " to " + in.endDate(),
            List.of(
                new Column("purchases", "Purchases (COGS)", "currency"),
                new Column("wastage", "Wastage", "currency")),
            List.of(row),
            new WidgetQuery(ToolId.GET_FOOD_COST.name(), in.toMap()));
    return new ToolResult(widget, List.of());
  }
}
