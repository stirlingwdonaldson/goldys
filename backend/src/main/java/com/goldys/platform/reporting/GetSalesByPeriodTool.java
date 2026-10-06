package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.DailySalesMetric;
import com.goldys.platform.semantic.SalesMetricsQuery;
import com.goldys.platform.widget.Point;
import com.goldys.platform.widget.Series;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a time-series widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final SalesMetricsQuery salesMetrics;

  public GetSalesByPeriodTool(SalesMetricsQuery salesMetrics) {
    this.salesMetrics = salesMetrics;
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
    List<Point> points = new ArrayList<>();
    List<LocalDate> unresolved = new ArrayList<>();
    for (DailySalesMetric m : salesMetrics.dailySales(in.startDate(), in.endDate())) {
      if (m.grossSales() == null) {
        unresolved.add(m.tradingDate());
      }
      points.add(new Point(m.tradingDate().toString(), m.grossSales()));
    }
    List<String> notices =
        unresolved.isEmpty()
            ? List.of()
            : List.of(unresolved.size() + " date(s) have no resolved total (unresolved conflict).");
    TimeSeriesWidgetSpec widget =
        new TimeSeriesWidgetSpec(
            UUID.randomUUID().toString(),
            "Daily sales",
            "Resolved gross sales per day.",
            List.of(new Series("grossSales", "Gross sales", points)),
            "currency",
            new WidgetQuery(ToolId.GET_SALES_BY_PERIOD.name(), in.toMap()));
    return new ToolResult(widget, notices);
  }
}
