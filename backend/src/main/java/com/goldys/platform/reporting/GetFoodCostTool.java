package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved food cost (COGS) and wastage for a date range, emitted as a table widget. */
@Component
public class GetFoodCostTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final MetricCatalog catalog;

  public GetFoodCostTool(MetricQueryService metrics, MetricCatalog catalog) {
    this.metrics = metrics;
    this.catalog = catalog;
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
    TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
    TimeSeriesResult purchases = query(MetricId.INVENTORY_PURCHASES, range);
    TimeSeriesResult wastage = query(MetricId.INVENTORY_WASTAGE, range);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("purchases", sum(purchases));
    row.put("wastage", sum(wastage));

    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Food cost",
            "Resolved food cost for " + in.startDate() + " to " + in.endDate(),
            List.of(
                new Column(
                    "purchases",
                    catalog.definition(MetricId.INVENTORY_PURCHASES).name(),
                    "currency"),
                new Column(
                    "wastage", catalog.definition(MetricId.INVENTORY_WASTAGE).name(), "currency")),
            List.of(row),
            new WidgetQuery(ToolId.GET_FOOD_COST.name(), in.toMap()));
    return new ToolResult(widget, notices(purchases, wastage));
  }

  private TimeSeriesResult query(MetricId id, TimeRange range) {
    return (TimeSeriesResult)
        metrics.query(new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null));
  }

  private static BigDecimal sum(TimeSeriesResult result) {
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries series : result.series()) {
      for (MetricPoint point : series.points()) {
        if (point.value() != null) {
          total = total.add(point.value());
          any = true;
        }
      }
    }
    return any ? total : null;
  }

  private static List<String> notices(TimeSeriesResult... results) {
    return List.of(results).stream().flatMap(r -> r.notices().stream()).toList();
  }
}
