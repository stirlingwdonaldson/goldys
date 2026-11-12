package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolved labour hours, cost, scheduled-vs-actual variance, and FOH/BOH percentages. */
@Component
public class GetLabourVarianceTool extends MetricReportingTool {

  public GetLabourVarianceTool(MetricQueryService metrics, WidgetRenderer renderer) {
    super(metrics, renderer);
  }

  @Override
  public ToolId id() {
    return ToolId.GET_LABOUR_VARIANCE;
  }

  @Override
  public String name() {
    return "get_labour_variance";
  }

  @Override
  public String description() {
    return "Resolved labour hours, cost, scheduled-vs-actual variance, and FOH/BOH labour "
        + "percentages for a date range.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetLabourVarianceInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("labour.cost");
  }

  @Override
  protected String renderType(List<MetricResult> results) {
    return "table";
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetLabourVarianceInput in)) {
      throw new IllegalArgumentException(
          "Expected GetLabourVarianceInput, got " + input.getClass().getSimpleName());
    }
    TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
    return List.of(
        q(MetricId.LABOUR_SCHEDULED_HOURS, range),
        q(MetricId.LABOUR_ACTUAL_HOURS, range),
        q(MetricId.LABOUR_COST, range),
        q(MetricId.LABOUR_HOURS_VARIANCE, range),
        q(MetricId.LABOUR_FOH_PERCENT, range),
        q(MetricId.LABOUR_BOH_PERCENT, range));
  }

  private static MetricQuery q(MetricId id, TimeRange range) {
    return new MetricQuery(id, range, TimeGrain.TOTAL, Set.of(), null);
  }
}
