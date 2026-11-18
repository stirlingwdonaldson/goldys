package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.*;
import com.goldys.platform.widget.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Converts authorized metric results into bounded charts without model-supplied numbers or joins.
 */
final class MetricChartRenderer {
  private final MetricCatalog catalog;

  MetricChartRenderer(MetricCatalog catalog) {
    this.catalog = catalog;
  }

  void validatePlan(List<MetricQuery> plan, ChartOptions options) {
    var first = plan.getFirst();
    for (var query : plan) {
      if (!unit(first.metric()).equals(unit(query.metric())))
        throw new IllegalArgumentException(
            "Charts require the same unit; use separate charts for different units");
      if (query.grain() != first.grain())
        throw new IllegalArgumentException(
            "Charts require the same grain; do not mix period totals and daily values");
      if (query.grain() == TimeGrain.DAY && days(query) > 366)
        throw new IllegalArgumentException(
            "Use WEEK or MONTH for charts spanning more than 366 days");
      if (options.alignment() != ChartOptions.Alignment.DATE) {
        if (query.grain() != TimeGrain.DAY || days(query) != days(first))
          throw new IllegalArgumentException("Aligned charts require equal-length DAY ranges");
        if (options.alignment() == ChartOptions.Alignment.WEEKDAY
            && (days(query) > 7
                || query.range().from().getDayOfWeek() != first.range().from().getDayOfWeek()))
          throw new IllegalArgumentException(
              "Weekday charts require matching weekdays in ranges of at most seven days");
      }
    }
    if ((options.kind() == ChartKind.LINE || options.kind() == ChartKind.AREA)
        && (first.grain() == TimeGrain.TOTAL
            || plan.stream().anyMatch(q -> q.metric() == MetricId.PRODUCT_TOP_SELLERS)))
      throw new IllegalArgumentException(
          "Line and area charts need ordered DAY/WEEK/MONTH values; use BAR for totals or rankings");
    if (options.stacked() || options.kind() == ChartKind.PIE || options.kind() == ChartKind.DONUT) {
      if (plan.size() != 1 || !additive(first.metric()))
        throw new IllegalArgumentException(
            "Part-to-whole and stacked charts require one additive metric query; use BAR or LINE instead");
      if (options.stacked() && first.dimensions().isEmpty())
        throw new IllegalArgumentException(
            "Stacking requires disjoint groups from one metric query");
    }
  }

  ChartWidgetSpec render(List<MetricQuery> plan, List<MetricResult> results, ChartOptions options) {
    if ((options.kind() == ChartKind.PIE || options.kind() == ChartKind.DONUT)
        && results.stream().anyMatch(r -> !r.provenance().missingPeriods().isEmpty()))
      throw new IllegalArgumentException(
          "Incomplete coverage cannot be shown as a complete whole; use BAR or LINE");
    List<Series> series = new ArrayList<>();
    boolean totals = plan.getFirst().grain() == TimeGrain.TOTAL;
    for (int i = 0; i < results.size(); i++) {
      var result = results.get(i);
      var query = plan.get(i);
      String label = catalog.definition(result.metric()).name() + " · " + range(query);
      if (result instanceof RankedListResult ranked) {
        if (plan.size() != 1 || options.kind() != ChartKind.BAR)
          throw new IllegalArgumentException(
              "Ranked top-seller results support BAR only; they are not a complete whole");
        if (ranked.items().stream().anyMatch(MetricRankedItem::hasConflict))
          throw new IllegalArgumentException(
              "Ranked data has unresolved conflicts; use the qualified table instead");
        series.add(
            new Series(
                "q" + i,
                label,
                ranked.items().stream().map(p -> new Point(p.label(), p.primary())).toList()));
      } else {
        var ts = (TimeSeriesResult) result;
        if (totals) {
          var points =
              ts.series().stream()
                  .map(
                      s -> {
                        if (s.points().size() > 1)
                          throw new IllegalArgumentException(
                              "TOTAL result contains multiple buckets");
                        return new Point(
                            s.dimensionValue() == null ? label : s.dimensionValue(),
                            s.points().isEmpty() ? null : s.points().getFirst().value());
                      })
                  .toList();
          series.add(new Series("q" + i, label, points));
        } else {
          for (var group : ts.series())
            series.add(
                new Series(
                    "s" + series.size(),
                    label + (group.dimensionValue() == null ? "" : " · " + group.dimensionValue()),
                    points(query, group, options)));
        }
      }
    }
    // Scalar totals are categories, not sparse single-point series with different category names.
    if (totals
        && plan.stream().allMatch(q -> q.dimensions().isEmpty())
        && results.stream().allMatch(r -> r instanceof TimeSeriesResult))
      series =
          new ArrayList<>(
              List.of(
                  new Series(
                      "values",
                      "Period totals",
                      series.stream().flatMap(s -> s.points().stream()).toList())));
    series =
        align(
            series,
            !totals
                && options.alignment() == ChartOptions.Alignment.DATE
                && results.stream().allMatch(r -> r instanceof TimeSeriesResult));
    validateShape(series, options);
    String format =
        switch (unit(plan.getFirst().metric())) {
          case "AUD" -> "currency";
          case "%" -> "percent";
          default -> "number";
        };
    String description =
        "Values in "
            + unit(plan.getFirst().metric())
            + ". Missing observations remain unavailable.";
    if (options.kind() == ChartKind.RADIAL)
      description += " Radial scale ends at the largest observed value; it is not a target.";
    if (options.kind() == ChartKind.PIE || options.kind() == ChartKind.DONUT)
      description += " Shares are of the selected query only.";
    return new ChartWidgetSpec(
        UUID.randomUUID().toString(),
        catalog.definition(plan.getFirst().metric()).name(),
        description,
        options.kind(),
        series,
        format,
        options.stacked(),
        options.horizontal(),
        null);
  }

  private List<Point> points(MetricQuery query, MetricSeries group, ChartOptions options) {
    Map<LocalDate, BigDecimal> values = new TreeMap<>();
    group.points().forEach(p -> values.put(p.bucketStart(), p.value()));
    if (query.grain() == TimeGrain.DAY)
      query
          .range()
          .from()
          .datesUntil(query.range().to().plusDays(1))
          .forEach(date -> values.putIfAbsent(date, null));
    return values.entrySet().stream()
        .map(
            p ->
                new Point(
                    switch (options.alignment()) {
                      case DATE -> p.getKey().toString();
                      case WEEKDAY ->
                          p.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                      case PERIOD ->
                          "Day "
                              + (p.getKey().toEpochDay() - query.range().from().toEpochDay() + 1);
                    },
                    p.getValue()))
        .toList();
  }

  private List<Series> align(List<Series> series, boolean chronological) {
    Set<String> categories = chronological ? new TreeSet<>() : new LinkedHashSet<>();
    for (var s : series) {
      Set<String> seen = new HashSet<>();
      for (var p : s.points()) {
        if (!seen.add(p.x()))
          throw new IllegalArgumentException(
              "Chart categories must be distinct; revise the query scope");
        categories.add(p.x());
      }
    }
    if (series.size() > 6 || categories.size() > 366)
      throw new IllegalArgumentException(
          "Charts support up to six series and 366 categories; use a coarser grain or a table");
    return series.stream()
        .map(
            s -> {
              Map<String, BigDecimal> values = new HashMap<>();
              s.points().forEach(p -> values.put(p.x(), p.y()));
              return new Series(
                  s.key(),
                  s.label(),
                  categories.stream().map(x -> new Point(x, values.get(x))).toList());
            })
        .toList();
  }

  private void validateShape(List<Series> series, ChartOptions options) {
    if (series.isEmpty() || series.getFirst().points().size() < 2)
      throw new IllegalArgumentException(
          "A chart needs at least two observations or categories; answer a single value directly");
    var points = series.stream().flatMap(s -> s.points().stream()).toList();
    if (points.stream().noneMatch(p -> p.y() != null))
      throw new IllegalArgumentException("No observed values to chart; report unavailable data");
    boolean polar =
        Set.of(ChartKind.PIE, ChartKind.DONUT, ChartKind.RADAR, ChartKind.RADIAL)
            .contains(options.kind());
    if (polar || options.stacked()) {
      if (points.stream().anyMatch(p -> p.y() == null || p.y().signum() < 0))
        throw new IllegalArgumentException(
            "This chart needs complete nonnegative data; use an unstacked BAR or LINE to preserve gaps or negatives");
    }
    if (polar && series.getFirst().points().size() > 12)
      throw new IllegalArgumentException(
          "Polar charts support up to 12 categories; use BAR or a table for larger results");
    if (options.kind() == ChartKind.RADAR && series.getFirst().points().size() < 3)
      throw new IllegalArgumentException("Radar charts need at least three comparable categories");
    if (Set.of(ChartKind.PIE, ChartKind.DONUT, ChartKind.RADIAL).contains(options.kind())) {
      if (series.size() != 1)
        throw new IllegalArgumentException(
            "This chart requires one series; use BAR or LINE for multiple series");
      if (points.stream().allMatch(p -> p.y().signum() == 0))
        throw new IllegalArgumentException("Zero-only data has no visible angular extent; use BAR");
    }
  }

  private String unit(MetricId metric) {
    return catalog
        .definition(metric == MetricId.PRODUCT_TOP_SELLERS ? MetricId.PRODUCT_SALES_AMOUNT : metric)
        .unit();
  }

  private boolean additive(MetricId metric) {
    return !"derived".equals(catalog.definition(metric).sourceDomain())
        && metric != MetricId.INVENTORY_STOCK_ON_HAND;
  }

  private static long days(MetricQuery query) {
    return query.range().to().toEpochDay() - query.range().from().toEpochDay() + 1;
  }

  private static String range(MetricQuery query) {
    return query.range().from() + " – " + query.range().to();
  }
}
