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

/** Resolved daily gross sales for a date range, emitted as a time-series widget. */
@Component
public class GetSalesByPeriodTool extends MetricReportingTool {

  public GetSalesByPeriodTool(MetricQueryService metrics, WidgetRenderer renderer) {
    super(metrics, renderer);
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
    return "Daily breakdown of gross, net or GST sales. For a period total use run_metric_analysis with TOTAL.";
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
  protected String renderType(List<MetricResult> results) {
    return "time-series";
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
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
    return List.of(
        new MetricQuery(
            in.metric(),
            new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
            TimeGrain.DAY,
            Set.of(),
            null));
  }
}
