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

/** Resolved labour hours/cost/variance for a date range, emitted as a table widget. */
@Component
public class GetLabourCostTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final MetricCatalog catalog;

  public GetLabourCostTool(MetricQueryService metrics, MetricCatalog catalog) {
    this.metrics = metrics;
    this.catalog = catalog;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_LABOUR_COST;
  }

  @Override
  public String name() {
    return "get_labour_cost";
  }

  @Override
  public String description() {
    return "Resolved labour hours, cost, and scheduled-vs-actual variance for a date range.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetLabourCostInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("labour.cost");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetLabourCostInput in)) {
      throw new IllegalArgumentException(
          "Expected GetLabourCostInput, got " + input.getClass().getSimpleName());
    }
    TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
    TimeSeriesResult scheduledHours = query(MetricId.LABOUR_SCHEDULED_HOURS, range);
    TimeSeriesResult actualHours = query(MetricId.LABOUR_ACTUAL_HOURS, range);
    TimeSeriesResult labourCost = query(MetricId.LABOUR_COST, range);
    TimeSeriesResult variance = query(MetricId.LABOUR_HOURS_VARIANCE, range);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("scheduledHours", sum(scheduledHours));
    row.put("actualHours", sum(actualHours));
    row.put("labourCost", sum(labourCost));
    row.put("variance", sum(variance));

    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Labour cost",
            "Resolved labour for " + in.startDate() + " to " + in.endDate(),
            List.of(
                new Column(
                    "scheduledHours",
                    catalog.definition(MetricId.LABOUR_SCHEDULED_HOURS).name(),
                    "decimal"),
                new Column(
                    "actualHours",
                    catalog.definition(MetricId.LABOUR_ACTUAL_HOURS).name(),
                    "decimal"),
                new Column(
                    "labourCost", catalog.definition(MetricId.LABOUR_COST).name(), "currency"),
                new Column(
                    "variance",
                    catalog.definition(MetricId.LABOUR_HOURS_VARIANCE).name(),
                    "decimal")),
            List.of(row),
            new WidgetQuery(ToolId.GET_LABOUR_COST.name(), in.toMap()));
    return new ToolResult(widget, notices(scheduledHours, actualHours, labourCost, variance));
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
