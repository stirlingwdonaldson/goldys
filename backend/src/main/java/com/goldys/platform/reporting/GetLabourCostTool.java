package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved labour hours/cost/variance for a date range, emitted as a table widget. */
@Component
public class GetLabourCostTool implements ReportingTool {
  private final LabourMetricsQuery labour;

  public GetLabourCostTool(LabourMetricsQuery labour) {
    this.labour = labour;
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
    BigDecimal cost = labour.labourCost(in.startDate(), in.endDate());
    List<String> notices =
        cost == null ? List.of("Labour cost is unknown for part of this period.") : List.of();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("scheduledHours", labour.scheduledHours(in.startDate(), in.endDate()));
    row.put("actualHours", labour.actualHours(in.startDate(), in.endDate()));
    row.put("labourCost", cost);
    row.put("variance", labour.scheduledVsActualVariance(in.startDate(), in.endDate()));

    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Labour cost",
            "Resolved labour for " + in.startDate() + " to " + in.endDate(),
            List.of(
                new Column("scheduledHours", "Scheduled hours", "decimal"),
                new Column("actualHours", "Actual hours", "decimal"),
                new Column("labourCost", "Labour cost", "currency"),
                new Column("variance", "Variance (hrs)", "decimal")),
            List.of(row),
            new WidgetQuery(ToolId.GET_LABOUR_COST.name(), in.toMap()));
    return new ToolResult(widget, notices);
  }
}
