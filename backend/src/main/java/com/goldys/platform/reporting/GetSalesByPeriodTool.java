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

/** Resolved daily gross sales for a date range, emitted as a time-series widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final WidgetRenderer renderer;

  public GetSalesByPeriodTool(MetricQueryService metrics, WidgetRenderer renderer) {
    this.metrics = metrics;
    this.renderer = renderer;
  }

  @Override
  public ToolId id() {
    return ToolId.GET_SALES_BY_PERIOD;
  }

  @Override
  public String name() {
    return "get_sales_by_period";
  }

  @Override
  public String description() {
    return "Resolved daily sales totals for a date range.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetSalesByPeriodInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("reconciliation.sales");
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    if (in.metric() != MetricId.SALES_GROSS
        && in.metric() != MetricId.SALES_NET
        && in.metric() != MetricId.SALES_GST) {
      throw new IllegalArgumentException(
          "Unsupported metric for get_sales_by_period: " + in.metric());
    }
    List<MetricResult> results = toMetricQueries(in).stream().map(metrics::query).toList();
    WidgetSpec widget = renderer.render(UUID.randomUUID().toString(), "time-series", results);
    return new ToolResult(
        widget,
        results.stream().flatMap(r -> r.notices().stream()).toList(),
        results.stream().map(MetricResult::provenance).toList(),
        List.of());
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    return List.of(
        new MetricQuery(
            in.metric(),
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            TimeGrain.DAY,
            Set.of(),
            null));
  }
}
