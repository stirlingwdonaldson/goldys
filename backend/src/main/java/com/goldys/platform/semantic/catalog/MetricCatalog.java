package com.goldys.platform.semantic.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The metric catalogue: every supported metric's {@link MetricDefinition}, keyed by {@link
 * MetricId}.
 */
@Component
public class MetricCatalog {
  private static final Set<TimeGrain> DAY_WEEK_MONTH =
      Set.of(TimeGrain.DAY, TimeGrain.WEEK, TimeGrain.MONTH);
  private static final Set<TimeGrain> DAY_ONLY = Set.of(TimeGrain.DAY);

  private final Map<MetricId, MetricDefinition> byId;

  public MetricCatalog() {
    this.byId =
        Stream.of(
                base(
                    MetricId.SALES_GROSS,
                    "Gross sales",
                    "Resolved gross sales incl. GST",
                    "totalSales",
                    "AUD",
                    "resolved_daily_sales",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "reconciliation.sales",
                    "gross includes GST"),
                base(
                    MetricId.SALES_NET,
                    "Net sales",
                    "Resolved net sales = gross − GST",
                    "netTotal",
                    "AUD",
                    "resolved_daily_sales",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "reconciliation.sales",
                    null),
                base(
                    MetricId.SALES_GST,
                    "GST",
                    "Resolved GST",
                    "gstTotal",
                    "AUD",
                    "resolved_daily_sales",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "reconciliation.sales",
                    null),
                base(
                    MetricId.RESERVATIONS_BOOKINGS,
                    "Bookings",
                    "Resolved bookings",
                    "bookings",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    null),
                base(
                    MetricId.RESERVATIONS_ATTENDED,
                    "Attended parties",
                    "Resolved attended",
                    "attended",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    null),
                base(
                    MetricId.RESERVATIONS_COVERS,
                    "Covers",
                    "Resolved covers (guests)",
                    "covers",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    null),
                base(
                    MetricId.RESERVATIONS_NO_SHOWS,
                    "No-shows",
                    "Resolved no-shows",
                    "noShows",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    null),
                base(
                    MetricId.LABOUR_SCHEDULED_HOURS,
                    "Scheduled hours",
                    "Resolved scheduled hours",
                    "scheduledHours",
                    "hours",
                    "resolved_labour_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.DEPARTMENT),
                    "labour.hours",
                    null),
                base(
                    MetricId.LABOUR_ACTUAL_HOURS,
                    "Actual hours",
                    "Resolved actual hours",
                    "actualHours",
                    "hours",
                    "resolved_labour_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.DEPARTMENT),
                    "labour.hours",
                    null),
                base(
                    MetricId.LABOUR_COST,
                    "Labour cost",
                    "Resolved actual cost",
                    "actualCost",
                    "AUD",
                    "resolved_labour_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.DEPARTMENT),
                    "labour.cost",
                    null),
                base(
                    MetricId.INVENTORY_PURCHASES,
                    "Purchases (COGS)",
                    "Resolved purchases",
                    "purchases",
                    "AUD",
                    "resolved_inventory_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "inventory.cost",
                    null),
                base(
                    MetricId.INVENTORY_WASTAGE,
                    "Wastage",
                    "Resolved wastage",
                    "wastage",
                    "AUD",
                    "resolved_inventory_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "inventory.cost",
                    null),
                base(
                    MetricId.INVENTORY_STOCK_ON_HAND,
                    "Closing stock",
                    "Resolved stock-on-hand",
                    "stockOnHand",
                    "AUD",
                    "resolved_inventory_day",
                    DAY_ONLY,
                    Set.of(),
                    "inventory.cost",
                    null),
                base(
                    MetricId.PRODUCT_SALES_AMOUNT,
                    "Product sales amount",
                    "Resolved product amount",
                    "amount",
                    "AUD",
                    "resolved_product_sales",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.PRODUCT),
                    "reconciliation.sales",
                    null),
                base(
                    MetricId.PRODUCT_SALES_QUANTITY,
                    "Product sales quantity",
                    "Resolved product quantity",
                    "quantitySold",
                    "units",
                    "resolved_product_sales",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.PRODUCT),
                    "reconciliation.sales",
                    null),
                derived(
                    MetricId.RESERVATIONS_NO_SHOW_RATE,
                    "No-show rate",
                    "no_shows ÷ bookings",
                    "%",
                    "reservations.metrics",
                    null),
                derived(
                    MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
                    "Booking-to-cover conversion",
                    "attended ÷ bookings",
                    "%",
                    "reservations.metrics",
                    null),
                derived(
                    MetricId.RESERVATIONS_AVG_PARTY_SIZE,
                    "Average party size",
                    "covers ÷ attended",
                    "ratio",
                    "reservations.metrics",
                    null),
                derived(
                    MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                    "Average spend per cover",
                    "sales.gross ÷ reservations.covers",
                    "AUD",
                    "reconciliation.sales",
                    null),
                derived(
                    MetricId.LABOUR_HOURS_PER_COVER,
                    "Hours per cover",
                    "labour.actual_hours ÷ reservations.covers",
                    "hours/cover",
                    "labour.hours",
                    null),
                derived(
                    MetricId.LABOUR_COST_PER_COVER,
                    "Cost per cover",
                    "labour.cost ÷ reservations.covers",
                    "AUD/cover",
                    "labour.cost",
                    null),
                derived(
                    MetricId.LABOUR_HOURS_VARIANCE,
                    "Hours variance",
                    "labour.scheduled_hours − labour.actual_hours",
                    "hours",
                    "labour.hours",
                    null),
                derived(
                    MetricId.LABOUR_FOH_PERCENT,
                    "FOH labour cost %",
                    "FOH labour.cost ÷ sales.gross",
                    "%",
                    "labour.cost",
                    null),
                derived(
                    MetricId.LABOUR_BOH_PERCENT,
                    "BOH labour cost %",
                    "BOH labour.cost ÷ sales.gross",
                    "%",
                    "labour.cost",
                    null),
                derived(
                    MetricId.INVENTORY_FOOD_COST_PERCENT,
                    "Food cost %",
                    "inventory.purchases ÷ sales.gross",
                    "%",
                    "inventory.cost",
                    null),
                derived(
                    MetricId.PRODUCT_TOP_SELLERS,
                    "Top sellers",
                    "ranked product list by summed resolved amount",
                    "list",
                    "reconciliation.sales",
                    null))
            .collect(Collectors.toUnmodifiableMap(MetricDefinition::id, Function.identity()));
  }

  public MetricDefinition definition(MetricId id) {
    MetricDefinition d = byId.get(id);
    if (d == null) {
      throw new IllegalArgumentException("Unknown metric: " + id);
    }
    return d;
  }

  public Set<MetricId> ids() {
    return byId.keySet();
  }

  private static MetricDefinition base(
      MetricId id,
      String name,
      String definition,
      String formula,
      String unit,
      String domain,
      Set<TimeGrain> grains,
      Set<Dimension> dimensions,
      String permission,
      String note) {
    return new MetricDefinition(
        id,
        name,
        definition,
        formula,
        unit,
        domain,
        dimensions,
        grains,
        permission,
        note == null ? List.of() : List.of(note),
        "1");
  }

  private static MetricDefinition derived(
      MetricId id, String name, String formula, String unit, String permission, String note) {
    return new MetricDefinition(
        id,
        name,
        formula,
        formula,
        unit,
        "derived",
        Set.of(),
        DAY_WEEK_MONTH,
        permission,
        note == null ? List.of() : List.of(note),
        "1");
  }
}
