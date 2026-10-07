package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.WidgetSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The general single-metric tool: query any catalogue metric and render it. */
@Component
public class GetMetricTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final WidgetRenderer renderer;
  private final MetricCatalog catalog;

  public GetMetricTool(MetricQueryService metrics, WidgetRenderer renderer, MetricCatalog catalog) {
    this.metrics = metrics;
    this.renderer = renderer;
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
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetMetricInput in)) {
      throw new IllegalArgumentException(
          "Expected GetMetricInput, got " + input.getClass().getSimpleName());
    }
    List<MetricResult> results = toMetricQueries(in).stream().map(metrics::query).toList();
    MetricResult r = results.get(0);
    String type = r instanceof RankedListResult ? "ranked-list" : "time-series";
    WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), type, results);
    List<MetricId> related = new ArrayList<>(catalog.related(r.metric()));
    return new ToolResult(widget, r.notices(), List.of(r.provenance()), related);
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
