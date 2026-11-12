package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** The general single-metric tool: query any catalogue metric and render it. */
@Component
public class GetMetricTool extends MetricReportingTool {
  private final MetricCatalog catalog;

  public GetMetricTool(MetricQueryService metrics, WidgetRenderer renderer, MetricCatalog catalog) {
    super(metrics, renderer);
    this.catalog = catalog;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_METRIC;
  }

  @Override
  public String name() {
    return "get_metric";
  }

  @Override
  public String description() {
    return "Query a single metric (by dotted id) over a date range and grain, with optional "
        + "dimensions. Returns the series/ranked list plus the metric's related metrics.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetMetricInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("conversational.chat");
  }

  @Override
  protected String renderType(List<MetricResult> results) {
    return results.get(0) instanceof RankedListResult ? "ranked-list" : "time-series";
  }

  @Override
  protected List<MetricId> relatedMetrics(List<MetricResult> results) {
    return new ArrayList<>(catalog.related(results.get(0).metric()));
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetMetricInput in)) {
      throw new IllegalArgumentException(
          "Expected GetMetricInput, got " + input.getClass().getSimpleName());
    }
    return List.of(
        new MetricQuery(
            in.metric(),
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            in.grain(),
            in.dimensions(),
            null));
  }
}
