package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.DailySalesResolved;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolved daily gross sales for a date range, emitted as a line-chart widget. */
@Component
public class GetSalesByPeriodTool implements ReportingTool {
  private final DailySalesReconciliationService reconciliation;

  public GetSalesByPeriodTool(DailySalesReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
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
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetSalesByPeriodInput in)) {
      throw new IllegalArgumentException(
          "Expected GetSalesByPeriodInput, got " + input.getClass().getSimpleName());
    }
    List<Map<String, Object>> points = new ArrayList<>();
    List<LocalDate> unresolved = new ArrayList<>();
    for (LocalDate d = in.startDate(); !d.isAfter(in.endDate()); d = d.plusDays(1)) {
      LocalDate date = d;
      reconciliation
          .resolved(date)
          .ifPresentOrElse(
              r -> {
                if (r.resolvedTotal() == null) {
                  unresolved.add(date);
                } else {
                  points.add(point(date, r));
                }
              },
              () -> unresolved.add(date));
    }
    List<String> notices =
        unresolved.isEmpty()
            ? List.of()
            : List.of(unresolved.size() + " date(s) have no resolved total (unresolved conflict).");
    WidgetSpec widget =
        new WidgetSpec(1, "line-chart", "Daily sales", "Resolved gross sales per day.", points);
    return new ToolResult(widget, notices);
  }

  private static Map<String, Object> point(LocalDate date, DailySalesResolved r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("date", date.toString());
    m.put("grossSales", r.resolvedTotal());
    m.put("source", r.authoritativeSource());
    return m;
  }
}
