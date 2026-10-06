package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.ResolvedDailySalesQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a line-chart widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final ResolvedDailySalesQuery resolved;

  public GetSalesByPeriodTool(ResolvedDailySalesQuery resolved) {
    this.resolved = resolved;
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
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    List<Map<String, Object>> points = new ArrayList<>();
    List<LocalDate> unresolved = new ArrayList<>();
    for (ResolvedDailySalesQuery.ResolvedDailySalesView v :
        resolved.between(in.startDate(), in.endDate())) {
      if (v.totalSales() == null) {
        unresolved.add(v.tradingDate());
      } else {
        points.add(point(v));
      }
    }
    List<String> notices =
        unresolved.isEmpty()
            ? List.of()
            : List.of(unresolved.size() + " date(s) have no resolved total (unresolved conflict).");
    WidgetSpec widget =
        new WidgetSpec(1, "line-chart", "Daily sales", "Resolved gross sales per day.", points);
    return new ToolResult(widget, notices);
  }

  private static Map<String, Object> point(ResolvedDailySalesQuery.ResolvedDailySalesView v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("date", v.tradingDate().toString());
    m.put("grossSales", v.totalSales());
    m.put("source", v.authoritativeSource());
    return m;
  }
}
