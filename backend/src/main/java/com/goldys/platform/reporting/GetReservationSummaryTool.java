package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.ReservationMetricsQuery;
import com.goldys.platform.semantic.ReservationSummary;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.WidgetQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolved reservation summary for a date, emitted as a table widget. */
@Component
public class GetReservationSummaryTool implements ReportingTool {
  private final ReservationMetricsQuery metrics;

  public GetReservationSummaryTool(ReservationMetricsQuery metrics) {
    this.metrics = metrics;
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
  public ToolResult execute(ToolInput input, UserRole role) {
    if (!(input instanceof GetReservationSummaryInput in)) {
      throw new IllegalArgumentException(
          "Expected GetReservationSummaryInput, got " + input.getClass().getSimpleName());
    }
    Optional<ReservationSummary> summary = metrics.summary(in.date());
    List<Map<String, Object>> rows =
        summary.map(GetReservationSummaryTool::toRow).map(List::of).orElse(List.of());
    List<String> notices =
        summary.isPresent() ? List.of() : List.of("No reservation data for " + in.date());

    TableWidgetSpec widget =
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Reservation summary",
            "Resolved reservation metrics for " + in.date(),
            columns(),
            rows,
            new WidgetQuery(ToolId.GET_RESERVATION_SUMMARY.name(), in.toMap()));
    return new ToolResult(widget, notices);
  }

  private static List<Column> columns() {
    return List.of(
        new Column("bookings", "Bookings", null),
        new Column("attended", "Attended", null),
        new Column("covers", "Covers", null),
        new Column("cancelled", "Cancelled", null),
        new Column("noShows", "No-shows", null),
        new Column("walkIns", "Walk-ins", null),
        new Column("avgPartySize", "Avg party size", "decimal"),
        new Column("noShowRate", "No-show rate", "percent"),
        new Column("bookingToCoverConversion", "Conversion", "percent"));
  }

  private static Map<String, Object> toRow(ReservationSummary s) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("bookings", s.bookings());
    row.put("attended", s.attended());
    row.put("covers", s.covers());
    row.put("cancelled", s.cancelled());
    row.put("noShows", s.noShows());
    row.put("walkIns", s.walkIns());
    row.put("avgPartySize", s.avgPartySize());
    row.put("noShowRate", s.noShowRate());
    row.put("bookingToCoverConversion", s.bookingToCoverConversion());
    return row;
  }
}
