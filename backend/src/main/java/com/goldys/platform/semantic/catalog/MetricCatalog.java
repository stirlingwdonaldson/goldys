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
      Set.of(TimeGrain.DAY, TimeGrain.WEEK, TimeGrain.MONTH, TimeGrain.TOTAL);
  private static final Set<TimeGrain> DAY_ONLY = Set.of(TimeGrain.DAY);
  private static final Map<MetricId, Set<MetricId>> RELATED =
      Map.of(
          MetricId.SALES_GROSS,
              Set.of(
                  MetricId.RESERVATIONS_COVERS,
                  MetricId.SALES_AVERAGE_SPEND_PER_COVER,
                  MetricId.PRODUCT_SALES_AMOUNT,
                  MetricId.LABOUR_FOH_PERCENT,
                  MetricId.LABOUR_BOH_PERCENT,
                  MetricId.INVENTORY_FOOD_COST_PERCENT),
          MetricId.RESERVATIONS_COVERS,
              Set.of(
                  MetricId.RESERVATIONS_BOOKINGS,
                  MetricId.RESERVATIONS_ATTENDED,
                  MetricId.RESERVATIONS_NO_SHOWS,
                  MetricId.SALES_AVERAGE_SPEND_PER_COVER),
          MetricId.RESERVATIONS_BOOKINGS,
              Set.of(
                  MetricId.RESERVATIONS_ATTENDED,
                  MetricId.RESERVATIONS_COVERS,
                  MetricId.RESERVATIONS_NO_SHOW_RATE),
          MetricId.LABOUR_COST,
              Set.of(
                  MetricId.LABOUR_SCHEDULED_HOURS,
                  MetricId.LABOUR_ACTUAL_HOURS,
                  MetricId.LABOUR_HOURS_VARIANCE,
                  MetricId.LABOUR_FOH_PERCENT,
                  MetricId.LABOUR_BOH_PERCENT),
          MetricId.INVENTORY_PURCHASES,
              Set.of(MetricId.INVENTORY_WASTAGE, MetricId.INVENTORY_FOOD_COST_PERCENT),
          MetricId.PRODUCT_SALES_AMOUNT,
              Set.of(MetricId.PRODUCT_SALES_QUANTITY, MetricId.PRODUCT_TOP_SELLERS));

  /** Ordered arithmetic operands shared by evaluation, trust and permission traversal. */
  private static final Map<MetricId, DerivedFormula> FORMULAS =
      Map.ofEntries(
          Map.entry(
              MetricId.RESERVATIONS_NO_SHOW_SHARE_ALL_BOOKINGS,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.RESERVATIONS_NO_SHOWS,
                  MetricId.RESERVATIONS_BOOKINGS)),
          Map.entry(
              MetricId.RESERVATIONS_NO_SHOW_RATE,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.RESERVATIONS_NO_SHOWS,
                  MetricId.RESERVATIONS_EXPECTED_BOOKINGS)),
          Map.entry(
              MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.RESERVATIONS_ATTENDED,
                  MetricId.RESERVATIONS_BOOKINGS)),
          Map.entry(
              MetricId.RESERVATIONS_AVG_PARTY_SIZE,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.RESERVATIONS_COVERS,
                  MetricId.RESERVATIONS_ATTENDED)),
          Map.entry(
              MetricId.SALES_AVERAGE_SPEND_PER_COVER,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.SALES_GROSS,
                  MetricId.RESERVATIONS_COVERS)),
          Map.entry(
              MetricId.SALES_FOOD_SHARE,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO, MetricId.SALES_FOOD, MetricId.SALES_NET)),
          Map.entry(
              MetricId.LABOUR_HOURS_PER_COVER,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.LABOUR_ACTUAL_HOURS,
                  MetricId.RESERVATIONS_COVERS)),
          Map.entry(
              MetricId.LABOUR_COST_PER_COVER,
              new DerivedFormula(
                  DerivedFormula.Operation.RATIO,
                  MetricId.LABOUR_COST,
                  MetricId.RESERVATIONS_COVERS)),
          Map.entry(
              MetricId.LABOUR_HOURS_VARIANCE,
              new DerivedFormula(
                  DerivedFormula.Operation.DIFFERENCE,
                  MetricId.LABOUR_SCHEDULED_HOURS,
                  MetricId.LABOUR_ACTUAL_HOURS)));

  // Bespoke reads retain their explicit lineage, including the withdrawn food-cost metric.
  private static final Map<MetricId, Set<MetricId>> SPECIAL_CONSTITUENTS =
      Map.of(
          MetricId.LABOUR_FOH_PERCENT, Set.of(MetricId.LABOUR_COST, MetricId.SALES_GROSS),
          MetricId.LABOUR_BOH_PERCENT, Set.of(MetricId.LABOUR_COST, MetricId.SALES_GROSS),
          MetricId.INVENTORY_FOOD_COST_PERCENT,
              Set.of(MetricId.INVENTORY_PURCHASES, MetricId.SALES_GROSS),
          MetricId.PRODUCT_TOP_SELLERS,
              Set.of(MetricId.PRODUCT_SALES_AMOUNT, MetricId.PRODUCT_SALES_QUANTITY));

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
                    "Gross includes GST. Trading policy: 02:00 Australia/Melbourne every day (simplified); date-only source reports retain supplied attribution until close-time evidence is available"),
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
                    "Excludes evidenced GST. Trading policy: 02:00 Australia/Melbourne every day (simplified); date-only source reports cannot be reattributed without close times"),
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
                    MetricId.SALES_FOOD,
                    "Food sales",
                    "Resolved food sales with reviewed effective mappings and observed ex-GST basis",
                    "food",
                    "AUD",
                    "resolved_reporting_sales",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "reconciliation.sales",
                    null),
                base(
                    MetricId.SALES_BEV,
                    "Beverage sales",
                    "Reviewed ex-GST beverage sales; 95% absolute eligible classification coverage required",
                    "bev",
                    "AUD",
                    "resolved_reporting_sales",
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
                    MetricId.RESERVATIONS_EXPECTED_BOOKINGS,
                    "Expected bookings",
                    "Bookings excluding cancellations and walk-ins",
                    "bookings - cancelled - walkIns",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    "Pending attendance outcomes are provisional; no-show requires a business declaration"),
                base(
                    MetricId.RESERVATIONS_ATTENDED,
                    "Attended parties",
                    "Declared attended reservation parties, not verified individual people",
                    "attended",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    "Counts reservation parties, not verified people or walk-ins. A zero does not establish zero actual attendance."),
                base(
                    MetricId.RESERVATIONS_COVERS,
                    "Attendance proxy",
                    "Stated party size of attended reservations; actual arrivals are rarely updated",
                    "covers",
                    "count",
                    "resolved_reservation_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.SERVICE_PERIOD),
                    "reservations.metrics",
                    "Attendance proxy from stated party sizes, not booked covers or verified arrivals. A zero does not establish zero actual attendance."),
                base(
                    MetricId.RESERVATIONS_BOOKED_COVERS,
                    "Booked covers",
                    "Resolved booked covers (party size excluding cancelled and walk-ins)",
                    "bookedCovers",
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
                    "Labour cost (wages only)",
                    "Reported wage cost; excludes super and other employment costs pending source confirmation",
                    "actualCost",
                    "AUD",
                    "resolved_labour_day",
                    DAY_WEEK_MONTH,
                    Set.of(Dimension.DEPARTMENT),
                    "labour.cost",
                    null),
                base(
                    MetricId.INVENTORY_PURCHASES,
                    "Invoice purchases",
                    "Invoiced purchases; not consumed cost or inventory valuation",
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
                    "Product amount tax basis is source-qualified; do not relabel it as net or ex-GST without tax evidence."),
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
                base(
                    MetricId.PAYMENTS_AMOUNT,
                    "Payments taken",
                    "Resolved payment amount across all tenders",
                    "amount",
                    "AUD",
                    "resolved_payment_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "payments.metrics",
                    null),
                base(
                    MetricId.PAYMENTS_TIP,
                    "Tips",
                    "Resolved tip amount across all tenders",
                    "tip",
                    "AUD",
                    "resolved_payment_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "payments.metrics",
                    null),
                base(
                    MetricId.DELETED_SALES_AMOUNT,
                    "Deleted sales",
                    "Resolved deleted-order amount incl. GST",
                    "totalIncTax",
                    "AUD",
                    "resolved_deleted_sale_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "deleted-sales.metrics",
                    null),
                base(
                    MetricId.SALE_ITEMS_AMOUNT,
                    "Items sold amount",
                    "Resolved line-item amount incl. GST",
                    "amount",
                    "AUD",
                    "resolved_sale_item_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "sale-items.metrics",
                    null),
                base(
                    MetricId.SALE_ITEMS_QUANTITY,
                    "Items sold quantity",
                    "Resolved line-item quantity",
                    "quantity",
                    "units",
                    "resolved_sale_item_day",
                    DAY_WEEK_MONTH,
                    Set.of(),
                    "sale-items.metrics",
                    null),
                derived(
                    MetricId.RESERVATIONS_NO_SHOW_SHARE_ALL_BOOKINGS,
                    "No-shows as share of all bookings",
                    "declared no_shows ÷ all bookings (including cancellations and walk-ins)",
                    "%",
                    "reservations.metrics",
                    "Distinct from operational no-show rate, whose denominator is expected bookings"),
                derived(
                    MetricId.RESERVATIONS_NO_SHOW_RATE,
                    "No-show rate",
                    "declared no_shows ÷ expected_bookings (excluding cancellations and walk-ins)",
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
                    MetricId.SALES_FOOD_SHARE,
                    "Food share",
                    "sales.food ÷ sales.net",
                    "%",
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
                    "Food cost % (unavailable)",
                    "Withdrawn: invoice purchases do not establish COGS",
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

  /** Validates a plan before any executor reads data; shared by single and composed queries. */
  public void validateQuery(MetricQuery query) {
    MetricDefinition definition = definition(query.metric());
    if (!definition.allowedGrains().contains(query.grain())) {
      throw new IllegalArgumentException(
          "grain " + query.grain() + " is not allowed for " + query.metric().value());
    }
    if (!definition.validDimensions().containsAll(query.dimensions())) {
      throw new IllegalArgumentException(
          "dimensions " + query.dimensions() + " are not allowed for " + query.metric().value());
    }
  }

  /** The metrics a result's provenance points to, so the model can chain a "why" question. */
  public Set<MetricId> related(MetricId id) {
    return RELATED.getOrDefault(id, Set.of());
  }

  /** The base operands a derived metric is computed from; empty for base metrics. */
  public Set<MetricId> constituents(MetricId id) {
    DerivedFormula formula = formula(id);
    return formula == null
        ? SPECIAL_CONSTITUENTS.getOrDefault(id, Set.of())
        : Set.of(formula.left(), formula.right());
  }

  DerivedFormula formula(MetricId id) {
    return FORMULAS.get(id);
  }

  /** All permission resources contributing to a metric, including transitive operands. */
  public Set<String> requiredPermissions(MetricId id) {
    Set<String> resources = new java.util.LinkedHashSet<>();
    collectPermissions(id, resources, new java.util.HashSet<>());
    return Set.copyOf(resources);
  }

  private void collectPermissions(MetricId id, Set<String> resources, Set<MetricId> visited) {
    if (!visited.add(id)) return;
    resources.add(definition(id).requiredPermission());
    for (MetricId operand : constituents(id)) collectPermissions(operand, resources, visited);
  }

  private static String definitionVersion(MetricId id) {
    return Set.of(
                MetricId.RESERVATIONS_NO_SHOW_RATE,
                MetricId.INVENTORY_FOOD_COST_PERCENT,
                MetricId.RESERVATIONS_COVERS,
                MetricId.LABOUR_COST)
            .contains(id)
        ? "2"
        : "1";
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
        definitionVersion(id));
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
        definitionVersion(id));
  }
}
