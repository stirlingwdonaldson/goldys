package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.WidgetSpec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved labour hours/cost/variance for a date range, emitted as a table widget. */
@Component
public class GetLabourCostTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final WidgetRenderer renderer;

  public GetLabourCostTool(MetricQueryService metrics, WidgetRenderer renderer) {
    this.metrics = metrics;
    this.renderer = renderer;
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
    List<MetricResult> results = toMetricQueries(in).stream().map(metrics::query).toList();
    WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), "table", results);
    return new ToolResult(
        widget,
        results.stream().flatMap(r -> r.notices().stream()).toList(),
        results.stream().map(MetricResult::provenance).toList(),
        List.of());
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetLabourCostInput in)) {
      throw new IllegalArgumentException(
          "Expected GetLabourCostInput, got " + input.getClass().getSimpleName());
    }
    TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
    return List.of(
        q(MetricId.LABOUR_SCHEDULED_HOURS, range),
        q(MetricId.LABOUR_ACTUAL_HOURS, range),
        q(MetricId.LABOUR_COST, range),
        q(MetricId.LABOUR_HOURS_VARIANCE, range));
  }

  private static MetricQuery q(MetricId id, TimeRange range) {
    return new MetricQuery(id, range, TimeGrain.DAY, Set.of(), null);
  }
}
