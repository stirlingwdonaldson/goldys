package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.ProductMetricsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Executes derived metrics as plain functions over base-metric results. */
@Component
public class DerivedMetricExecutor implements MetricExecutor {
  private static final int SCALE = 4;

  private final Map<MetricId, MetricExecutor> base;
  private final LabourMetricsQuery labour;
  private final ProductMetricsQuery product;
  private final MetricCatalog catalog;

  public DerivedMetricExecutor(
      ReservationMetricExecutor reservations,
      SalesMetricExecutor sales,
      LabourMetricExecutor labourExec,
      InventoryMetricExecutor inventory,
      ProductMetricExecutor productExec,
      LabourMetricsQuery labour,
      ProductMetricsQuery product,
      MetricCatalog catalog) {
    this.labour = labour;
    this.product = product;
    this.catalog = catalog;
    this.base =
        Map.of(
            MetricId.RESERVATIONS_BOOKINGS, reservations,
            MetricId.RESERVATIONS_ATTENDED, reservations,
            MetricId.RESERVATIONS_COVERS, reservations,
            MetricId.RESERVATIONS_NO_SHOWS, reservations,
            MetricId.SALES_GROSS, sales,
            MetricId.LABOUR_SCHEDULED_HOURS, labourExec,
            MetricId.LABOUR_ACTUAL_HOURS, labourExec,
            MetricId.LABOUR_COST, labourExec,
            MetricId.INVENTORY_PURCHASES, inventory);
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.RESERVATIONS_NO_SHOW_RATE,
        MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
        MetricId.RESERVATIONS_AVG_PARTY_SIZE,
        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
        MetricId.LABOUR_HOURS_PER_COVER,
        MetricId.LABOUR_COST_PER_COVER,
        MetricId.LABOUR_HOURS_VARIANCE,
        MetricId.LABOUR_FOH_PERCENT,
        MetricId.LABOUR_BOH_PERCENT,
        MetricId.INVENTORY_FOOD_COST_PERCENT,
        MetricId.PRODUCT_TOP_SELLERS);
  }

  @Override
  public MetricResult evaluate(MetricQuery query) {
    return switch (query.metric()) {
      case RESERVATIONS_NO_SHOW_RATE ->
          ratio(query, MetricId.RESERVATIONS_NO_SHOWS, MetricId.RESERVATIONS_BOOKINGS);
      case RESERVATIONS_BOOKING_TO_COVER_CONVERSION ->
          ratio(query, MetricId.RESERVATIONS_ATTENDED, MetricId.RESERVATIONS_BOOKINGS);
      case RESERVATIONS_AVG_PARTY_SIZE ->
          ratio(query, MetricId.RESERVATIONS_COVERS, MetricId.RESERVATIONS_ATTENDED);
      case SALES_AVERAGE_SPEND_PER_COVER ->
          ratio(query, MetricId.SALES_GROSS, MetricId.RESERVATIONS_COVERS);
      case LABOUR_HOURS_PER_COVER ->
          ratio(query, MetricId.LABOUR_ACTUAL_HOURS, MetricId.RESERVATIONS_COVERS);
      case LABOUR_COST_PER_COVER ->
          ratio(query, MetricId.LABOUR_COST, MetricId.RESERVATIONS_COVERS);
      case LABOUR_HOURS_VARIANCE ->
          difference(query, MetricId.LABOUR_SCHEDULED_HOURS, MetricId.LABOUR_ACTUAL_HOURS);
      case INVENTORY_FOOD_COST_PERCENT ->
          ratio(query, MetricId.INVENTORY_PURCHASES, MetricId.SALES_GROSS);
      case LABOUR_FOH_PERCENT -> departmentPercent(query, "FOH");
      case LABOUR_BOH_PERCENT -> departmentPercent(query, "BOH");
      case PRODUCT_TOP_SELLERS -> topSellers(query);
      default -> throw new IllegalArgumentException("Not a derived metric: " + query.metric());
    };
  }

  /** Per-day value map for a base metric, obtained by querying its base executor at DAY grain. */
  private Map<LocalDate, BigDecimal> daySeries(MetricQuery query, MetricId operand) {
    MetricQuery sub = new MetricQuery(operand, query.range(), TimeGrain.DAY, Set.of(), null);
    TimeSeriesResult r = (TimeSeriesResult) base.get(operand).evaluate(sub);
    return r.series().stream()
        .flatMap(s -> s.points().stream())
        .collect(Collectors.toMap(MetricPoint::bucketStart, MetricPoint::value, (a, b) -> a));
  }

  /** numerator ÷ denominator, computed per bucket at the requested grain (ratio of bucket sums). */
  private TimeSeriesResult ratio(MetricQuery query, MetricId numId, MetricId denId) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    GrainAggregator.Bucket num =
        GrainAggregator.sum(daySeries(query, numId), from, to, query.grain());
    GrainAggregator.Bucket den =
        GrainAggregator.sum(daySeries(query, denId), from, to, query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (int i = 0; i < num.points().size(); i++) {
      MetricPoint np = num.points().get(i);
      MetricPoint dp = den.points().get(i);
      BigDecimal v =
          (np.value() == null || dp.value() == null || dp.value().signum() == 0)
              ? null
              : np.value().divide(dp.value(), SCALE, RoundingMode.HALF_UP);
      points.add(new MetricPoint(np.bucketStart(), v));
      if (v == null) {
        notices.add("denominator or input unresolved for " + np.bucketStart());
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  /** a − b, per bucket at the requested grain. */
  private TimeSeriesResult difference(MetricQuery query, MetricId aId, MetricId bId) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    GrainAggregator.Bucket a = GrainAggregator.sum(daySeries(query, aId), from, to, query.grain());
    GrainAggregator.Bucket b = GrainAggregator.sum(daySeries(query, bId), from, to, query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (int i = 0; i < a.points().size(); i++) {
      BigDecimal av = a.points().get(i).value();
      BigDecimal bv = b.points().get(i).value();
      BigDecimal v = (av == null || bv == null) ? null : av.subtract(bv);
      points.add(new MetricPoint(a.points().get(i).bucketStart(), v));
      if (v == null) {
        notices.add("input unresolved for " + a.points().get(i).bucketStart());
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  /** department actual cost ÷ gross sales, per bucket at the requested grain. */
  private TimeSeriesResult departmentPercent(MetricQuery query, String department) {
    LocalDate from = query.range().from();
    LocalDate to = query.range().to();
    Map<LocalDate, BigDecimal> deptCost = new LinkedHashMap<>();
    Map<LocalDate, Boolean> unresolved = new HashMap<>();
    for (LabourMetric m : labour.dailyLabour(from, to)) {
      if (!department.equals(m.department())) {
        continue;
      }
      if (m.actualCost() == null) {
        unresolved.put(m.date(), true);
        deptCost.remove(m.date());
      } else if (!unresolved.getOrDefault(m.date(), false)) {
        deptCost.merge(m.date(), m.actualCost(), BigDecimal::add);
      }
    }
    GrainAggregator.Bucket dept = GrainAggregator.sum(deptCost, from, to, query.grain());
    GrainAggregator.Bucket gross =
        GrainAggregator.sum(daySeries(query, MetricId.SALES_GROSS), from, to, query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (int i = 0; i < dept.points().size(); i++) {
      BigDecimal dv = dept.points().get(i).value();
      BigDecimal gv = gross.points().get(i).value();
      BigDecimal v =
          (dv == null || gv == null || gv.signum() == 0)
              ? null
              : dv.divide(gv, SCALE, RoundingMode.HALF_UP);
      points.add(new MetricPoint(dept.points().get(i).bucketStart(), v));
      if (v == null) {
        notices.add("unresolved cost or zero gross for " + dept.points().get(i).bucketStart());
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  private RankedListResult topSellers(MetricQuery query) {
    List<MetricRankedItem> items =
        product.topSellers(query.range().from(), query.range().to(), 5).stream()
            .map(
                t ->
                    new MetricRankedItem(
                        t.productName(), t.amount(), t.quantitySold(), t.hasConflict()))
            .toList();
    return new RankedListResult(query.metric(), items, List.of(), provenance(query));
  }

  private MetricProvenance provenance(MetricQuery query) {
    MetricDefinition d = catalog.definition(query.metric());
    return new MetricProvenance(
        query.metric(),
        d.version(),
        query.range(),
        query.grain(),
        d.sourceDomain(),
        // TODO(provenance): plumb max(resolved_at) from the *MetricsQuery read, not Instant.EPOCH
        Instant.EPOCH,
        List.of(),
        d.version());
  }
}
