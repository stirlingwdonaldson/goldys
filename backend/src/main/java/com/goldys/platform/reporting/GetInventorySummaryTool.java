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

/** Resolved invoice purchases and wastage for a date range. */
@Component
public class GetInventorySummaryTool extends MetricReportingTool {

  public GetInventorySummaryTool(MetricQueryService metrics, WidgetRenderer renderer) {
    super(metrics, renderer);
  }

  @Override
  public ToolId id() {
    return ToolId.GET_INVENTORY_SUMMARY;
  }

  @Override
  public String name() {
    return "get_inventory_summary";
  }

  @Override
  public String description() {
    return "Resolved invoice purchases and wastage for a date range; food cost percentage is unavailable.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetInventorySummaryInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("inventory.cost");
  }

  @Override
  protected String renderType(List<MetricResult> results) {
    return "table";
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetInventorySummaryInput in)) {
      throw new IllegalArgumentException(
          "Expected GetInventorySummaryInput, got " + input.getClass().getSimpleName());
    }
    TimeRange range = new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR);
    return List.of(q(MetricId.INVENTORY_PURCHASES, range), q(MetricId.INVENTORY_WASTAGE, range));
  }

  private static MetricQuery q(MetricId id, TimeRange range) {
    return new MetricQuery(id, range, TimeGrain.TOTAL, Set.of(), null);
  }
}
