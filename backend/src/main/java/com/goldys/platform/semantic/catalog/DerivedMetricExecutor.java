package com.goldys.platform.semantic.catalog;

import com.goldys.platform.semantic.LabourMetric;
import com.goldys.platform.semantic.LabourMetricsQuery;
import com.goldys.platform.semantic.MissingDataStatus;
import com.goldys.platform.semantic.ProductMetricsQuery;
import com.goldys.platform.semantic.ProductRanking;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
      FoodBevMetricExecutor foodBev,
      LabourMetricsQuery labour,
      ProductMetricsQuery product,
      MetricCatalog catalog) {
    this.labour = labour;
    this.product = product;
    this.catalog = catalog;
    this.base =
        Map.ofEntries(
            Map.entry(MetricId.RESERVATIONS_BOOKINGS, reservations),
            Map.entry(MetricId.RESERVATIONS_EXPECTED_BOOKINGS, reservations),
            Map.entry(MetricId.RESERVATIONS_ATTENDED, reservations),
            Map.entry(MetricId.RESERVATIONS_COVERS, reservations),
            Map.entry(MetricId.RESERVATIONS_NO_SHOWS, reservations),
            Map.entry(MetricId.SALES_GROSS, sales),
            Map.entry(MetricId.SALES_NET, sales),
            Map.entry(MetricId.SALES_FOOD, foodBev),
            Map.entry(MetricId.LABOUR_SCHEDULED_HOURS, labourExec),
            Map.entry(MetricId.LABOUR_ACTUAL_HOURS, labourExec),
            Map.entry(MetricId.LABOUR_COST, labourExec),
            Map.entry(MetricId.INVENTORY_PURCHASES, inventory));
  }

  @Override
  public Set<MetricId> ids() {
    return Set.of(
        MetricId.RESERVATIONS_NO_SHOW_RATE,
        MetricId.RESERVATIONS_BOOKING_TO_COVER_CONVERSION,
        MetricId.RESERVATIONS_AVG_PARTY_SIZE,
        MetricId.SALES_AVERAGE_SPEND_PER_COVER,
        MetricId.SALES_FOOD_SHARE,
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
          ratio(query, MetricId.RESERVATIONS_NO_SHOWS, MetricId.RESERVATIONS_EXPECTED_BOOKINGS);
      case RESERVATIONS_BOOKING_TO_COVER_CONVERSION ->
          ratio(query, MetricId.RESERVATIONS_ATTENDED, MetricId.RESERVATIONS_BOOKINGS);
      case RESERVATIONS_AVG_PARTY_SIZE ->
          ratio(query, MetricId.RESERVATIONS_COVERS, MetricId.RESERVATIONS_ATTENDED);
      case SALES_AVERAGE_SPEND_PER_COVER ->
          ratio(query, MetricId.SALES_GROSS, MetricId.RESERVATIONS_COVERS);
      case SALES_FOOD_SHARE -> ratio(query, MetricId.SALES_FOOD, MetricId.SALES_NET);
      case LABOUR_HOURS_PER_COVER ->
          ratio(query, MetricId.LABOUR_ACTUAL_HOURS, MetricId.RESERVATIONS_COVERS);
      case LABOUR_COST_PER_COVER ->
          ratio(query, MetricId.LABOUR_COST, MetricId.RESERVATIONS_COVERS);
      case LABOUR_HOURS_VARIANCE ->
          difference(query, MetricId.LABOUR_SCHEDULED_HOURS, MetricId.LABOUR_ACTUAL_HOURS);
      case INVENTORY_FOOD_COST_PERCENT ->
          new TimeSeriesResult(
              query.metric(),
              List.of(
                  new MetricSeries(
                      null,
                      List.of(
                          new MetricPoint(
                              query.range().from(), null, MissingDataStatus.UNRESOLVED)))),
              List.of(
                  "Food cost % withdrawn: invoice purchases are not COGS; valuation evidence is required"),
              provenance(query));
      case LABOUR_FOH_PERCENT -> departmentPercent(query, "FOH");
      case LABOUR_BOH_PERCENT -> departmentPercent(query, "BOH");
      case PRODUCT_TOP_SELLERS -> topSellers(query);
      default -> throw new IllegalArgumentException("Not a derived metric: " + query.metric());
    };
  }

  /**
   * Per-day value map for a base metric, obtained by querying its base executor at DAY grain. A day
   * with no resolved value stays present with a {@code null} value so downstream {@link
   * GrainAggregator} can flag it {@code UNRESOLVED} rather than silently zeroing the gap. Built
   * with an explicit loop because {@code Collectors.toMap} rejects null values.
   */
  private Map<LocalDate, BigDecimal> daySeries(MetricQuery query, MetricId operand) {
    MetricQuery sub = new MetricQuery(operand, query.range(), TimeGrain.DAY, Set.of(), null);
    TimeSeriesResult r = (TimeSeriesResult) base.get(operand).evaluate(sub);
    Map<LocalDate, BigDecimal> byDay = new HashMap<>();
    for (MetricSeries series : r.series()) {
      for (MetricPoint point : series.points()) {
        byDay.putIfAbsent(point.bucketStart(), point.value());
      }
    }
    return byDay;
  }

  /**
   * numerator ÷ denominator, computed per bucket at the requested grain. Both operands are summed
   * over the <em>same</em> set of days — the days on which both have a resolved value — so a week
   * with seven days of sales but one day of covers no longer yields a spend-per-cover ~7× too high.
   * A bucket with no shared coverage is a null point ({@code UNRESOLVED} when at least one operand
   * had data, {@code NOT_RECEIVED} otherwise); a bucket with partial coverage still returns its
   * value but carries a notice naming the excluded days.
   */
  private TimeSeriesResult ratio(MetricQuery query, MetricId numId, MetricId denId) {
    List<Aligned> aligned =
        align(
            daySeries(query, numId),
            daySeries(query, denId),
            query.range().from(),
            query.range().to(),
            query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (Aligned a : aligned) {
      if (a.commonDays == 0) {
        points.add(
            new MetricPoint(
                a.start,
                null,
                a.anyData ? MissingDataStatus.UNRESOLVED : MissingDataStatus.NOT_RECEIVED));
        notices.add("no shared coverage for " + a.start);
      } else if (!a.excludedDays.isEmpty()) {
        points.add(new MetricPoint(a.start, null, MissingDataStatus.UNRESOLVED));
        partialCoverageNotice(notices, a);
        notices.add(
            "Ratio unavailable: missing inputs have no validated five-percentage-point uncertainty bound");
      } else if (a.denominator.signum() == 0) {
        points.add(new MetricPoint(a.start, null, MissingDataStatus.UNRESOLVED));
        notices.add("zero denominator for " + a.start);
      } else {
        points.add(
            new MetricPoint(
                a.start, a.numerator.divide(a.denominator, SCALE, RoundingMode.HALF_UP)));
        partialCoverageNotice(notices, a);
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  /** a − b, per bucket at the requested grain, over the days where both operands resolve. */
  private TimeSeriesResult difference(MetricQuery query, MetricId aId, MetricId bId) {
    List<Aligned> aligned =
        align(
            daySeries(query, aId),
            daySeries(query, bId),
            query.range().from(),
            query.range().to(),
            query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (Aligned a : aligned) {
      if (a.commonDays == 0) {
        points.add(
            new MetricPoint(
                a.start,
                null,
                a.anyData ? MissingDataStatus.UNRESOLVED : MissingDataStatus.NOT_RECEIVED));
        notices.add("no shared coverage for " + a.start);
      } else {
        points.add(new MetricPoint(a.start, a.numerator.subtract(a.denominator)));
        partialCoverageNotice(notices, a);
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  /** department actual cost ÷ gross sales, per bucket at the requested grain, over shared days. */
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
    List<Aligned> aligned =
        align(deptCost, daySeries(query, MetricId.SALES_GROSS), from, to, query.grain());
    List<MetricPoint> points = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (Aligned a : aligned) {
      if (a.commonDays == 0) {
        points.add(
            new MetricPoint(
                a.start,
                null,
                a.anyData ? MissingDataStatus.UNRESOLVED : MissingDataStatus.NOT_RECEIVED));
        notices.add("no shared coverage for " + a.start);
      } else if (!a.excludedDays.isEmpty()) {
        points.add(new MetricPoint(a.start, null, MissingDataStatus.UNRESOLVED));
        partialCoverageNotice(notices, a);
        notices.add(
            "Ratio unavailable: missing inputs have no validated five-percentage-point uncertainty bound");
      } else if (a.denominator.signum() == 0) {
        points.add(new MetricPoint(a.start, null, MissingDataStatus.UNRESOLVED));
        notices.add("zero gross sales for " + a.start);
      } else {
        points.add(
            new MetricPoint(
                a.start, a.numerator.divide(a.denominator, SCALE, RoundingMode.HALF_UP)));
        partialCoverageNotice(notices, a);
      }
    }
    return new TimeSeriesResult(
        query.metric(), List.of(new MetricSeries(null, points)), notices, provenance(query));
  }

  private static void partialCoverageNotice(List<String> notices, Aligned a) {
    if (!a.excludedDays.isEmpty()) {
      notices.add("partial coverage for " + a.start + ": excluded days " + a.excludedDays);
    }
  }

  /**
   * Buckets two per-day operand maps at the requested grain, summing each operand over the days on
   * which <em>both</em> have a resolved value. Days outside that shared set (one operand missing or
   * unresolved) are recorded as excluded so the caller can flag partial coverage instead of
   * silently presenting a ratio computed over mismatched day sets.
   */
  private static List<Aligned> align(
      Map<LocalDate, BigDecimal> numerator,
      Map<LocalDate, BigDecimal> denominator,
      LocalDate from,
      LocalDate to,
      TimeGrain grain) {
    TreeMap<LocalDate, Aligned> buckets = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      Aligned a =
          buckets.computeIfAbsent(
              grain == TimeGrain.TOTAL ? from : GrainAggregator.bucketStart(d, grain),
              Aligned::new);
      BigDecimal n = numerator.get(d);
      BigDecimal de = denominator.get(d);
      boolean nResolved = n != null;
      boolean dResolved = de != null;
      if (nResolved && dResolved) {
        a.numerator = a.numerator.add(n);
        a.denominator = a.denominator.add(de);
        a.commonDays++;
        a.anyData = true;
      } else {
        a.excludedDays.add(d);
        if (nResolved || dResolved) {
          a.anyData = true;
        }
      }
    }
    return List.copyOf(buckets.values());
  }

  /** One grain bucket's two operands summed over their shared (both-resolved) days. */
  private static final class Aligned {
    final LocalDate start;
    BigDecimal numerator = BigDecimal.ZERO;
    BigDecimal denominator = BigDecimal.ZERO;
    int commonDays;
    boolean anyData;
    final List<LocalDate> excludedDays = new ArrayList<>();

    Aligned(LocalDate start) {
      this.start = start;
    }
  }

  private RankedListResult topSellers(MetricQuery query) {
    ProductRanking ranking =
        product.qualifiedTopSellers(query.range().from(), query.range().to(), 5);
    List<MetricRankedItem> items =
        ranking.items().stream()
            .map(
                t ->
                    new MetricRankedItem(
                        t.productName(), t.amount(), t.quantitySold(), t.hasConflict()))
            .toList();
    return new RankedListResult(
        query.metric(),
        items,
        ranking.notices(),
        provenance(query, ranking.coverage().missingDates()).withCoverage(ranking.coverage()));
  }

  private MetricProvenance provenance(MetricQuery query) {
    return provenance(query, List.of());
  }

  private MetricProvenance provenance(MetricQuery query, List<LocalDate> missingDates) {
    MetricDefinition d = catalog.definition(query.metric());
    return new MetricProvenance(
        query.metric(),
        d.version(),
        query.range(),
        query.grain(),
        d.sourceDomain(),
        // TODO(provenance): plumb max(resolved_at) from the *MetricsQuery read, not Instant.EPOCH
        null,
        missingDates,
        d.version());
  }
}
