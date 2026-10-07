package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.Point;
import com.goldys.platform.widget.Series;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a time-series widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final MetricCatalog catalog;

  public GetSalesByPeriodTool(MetricQueryService metrics, MetricCatalog catalog) {
    this.metrics = metrics;
    this.catalog = catalog;
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
    TimeSeriesResult result =
        (TimeSeriesResult)
            metrics.query(
                new MetricQuery(
                    in.metric(),
                    new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
                    TimeGrain.DAY,
                    Set.of(),
                    null));

    String metricName = catalog.definition(in.metric()).name();
    List<Point> points =
        result.series().get(0).points().stream()
            .map(p -> new Point(p.bucketStart().toString(), p.value()))
            .toList();
    TimeSeriesWidgetSpec widget =
        new TimeSeriesWidgetSpec(
            UUID.randomUUID().toString(),
            "Daily sales",
            "Resolved " + metricName.toLowerCase() + " per day.",
            List.of(new Series("grossSales", metricName, points)),
            "currency",
            new WidgetQuery(ToolId.GET_SALES_BY_PERIOD.name(), in.toMap()));
    return new ToolResult(widget, result.notices());
  }
}
