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

/**
 * Resolved reservation summary for a date, emitted as a table widget over the catalogue-metric
 * subset (bookings, attended, covers, no-shows). The non-metric columns (cancelled, walk-ins) and
 * the derived ratios are deferred — see the dashboards design spec §18.
 */
@Component
public class GetReservationSummaryTool extends MetricReportingTool {

  public GetReservationSummaryTool(MetricQueryService metrics, WidgetRenderer renderer) {
    super(metrics, renderer);
  }

  @Override
  public ToolId id() {
    return ToolId.GET_RESERVATION_SUMMARY;
  }

  @Override
  public String name() {
    return "get_reservation_summary";
  }

  @Override
  public String description() {
    return "Resolved reservation summary for a date: bookings, covers, no-shows, party size, and conversion.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return GetReservationSummaryInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("reservations.metrics");
  }

  @Override
  protected String renderType(List<MetricResult> results) {
    return "table";
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof GetReservationSummaryInput in)) {
      throw new IllegalArgumentException(
          "Expected GetReservationSummaryInput, got " + input.getClass().getSimpleName());
    }
    TimeRange range = new TimeRange(in.date(), in.date(), Calendar.CALENDAR);
    return List.of(
        q(MetricId.RESERVATIONS_BOOKINGS, range),
        q(MetricId.RESERVATIONS_ATTENDED, range),
        q(MetricId.RESERVATIONS_COVERS, range),
        q(MetricId.RESERVATIONS_NO_SHOWS, range));
  }

  private static MetricQuery q(MetricId id, TimeRange range) {
    return new MetricQuery(id, range, TimeGrain.TOTAL, Set.of(), null);
  }
}
