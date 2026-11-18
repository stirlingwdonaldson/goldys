package com.goldys.platform.reporting;

import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.RankedListResult;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.BarChartWidgetSpec;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.Point;
import com.goldys.platform.widget.RankedItem;
import com.goldys.platform.widget.RankedListWidgetSpec;
import com.goldys.platform.widget.Series;
import com.goldys.platform.widget.StatWidgetSpec;
import com.goldys.platform.widget.TableWidgetSpec;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import com.goldys.platform.widget.WidgetSpec;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * The single place a bounded {@code List<MetricQuery>} result becomes a {@link WidgetSpec}. Every
 * widget path — saved dashboards and AI tools — renders through here, so no widget can carry
 * executable code or a free-form query.
 */
@Component
public class WidgetRenderer {
  private final MetricCatalog catalog;

  public WidgetRenderer(MetricCatalog catalog) {
    this.catalog = catalog;
  }

  public WidgetSpec render(String widgetId, String renderType, List<MetricResult> results) {
    if (results.isEmpty()) throw new IllegalArgumentException("widget has no queries");
    if (results.size() == 1) return single(widgetId, renderType, results.get(0));
    return composite(widgetId, renderType, results);
  }

  private WidgetSpec single(String id, String type, MetricResult result) {
    if (result instanceof RankedListResult r) {
      if (!"ranked-list".equals(type)) {
        throw new IllegalArgumentException(
            "metric " + result.metric() + " requires render type 'ranked-list'");
      }
      boolean productRanking =
          result.metric() == MetricId.PRODUCT_SALES_AMOUNT
              || result.metric() == MetricId.PRODUCT_TOP_SELLERS;
      String primaryUnit =
          catalog
              .definition(productRanking ? MetricId.PRODUCT_SALES_AMOUNT : result.metric())
              .unit();
      String secondaryUnit =
          productRanking ? catalog.definition(MetricId.PRODUCT_SALES_QUANTITY).unit() : null;
      List<RankedItem> items =
          r.items().stream()
              .map(
                  i ->
                      new RankedItem(
                          i.label(),
                          str(i.primary(), primaryUnit),
                          str(i.secondary(), secondaryUnit),
                          i.hasConflict() ? "unresolved" : null))
              .toList();
      return new RankedListWidgetSpec(
          id,
          label(result),
          productRanking
              ? "Primary is total product sales amount ("
                  + primaryUnit
                  + "); secondary is quantity sold ("
                  + secondaryUnit
                  + "). Amounts retain source tax basis."
              : "Primary is " + label(result) + " (" + primaryUnit + ").",
          items,
          null);
    }
    TimeSeriesResult ts = (TimeSeriesResult) result;
    return switch (type) {
      case "time-series" ->
          new TimeSeriesWidgetSpec(id, label(result), null, seriesOf(ts), format(result), null);
      case "bar-chart" ->
          new BarChartWidgetSpec(
              id, label(result), null, seriesOf(ts), format(result), false, null);
      case "stat" ->
          new StatWidgetSpec(id, label(result), null, total(ts), format(result), null, null);
      case "table" ->
          new TableWidgetSpec(
              id, label(result), null, columnsOf(List.of(result)), rowsOf(List.of(result)), null);
      default ->
          throw new IllegalArgumentException("unsupported render type for time-series: " + type);
    };
  }

  private WidgetSpec composite(String id, String type, List<MetricResult> results) {
    for (MetricResult r : results) {
      if (!(r instanceof TimeSeriesResult)) {
        throw new IllegalArgumentException(
            "composite widgets require time-series metrics; " + r.metric() + " is not");
      }
    }
    return switch (type) {
      case "weekday-comparison" -> weekdayComparison(id, results);
      case "table" ->
          new TableWidgetSpec(
              id, compositeLabel(results), null, columnsOf(results), rowsOf(results), null);
      case "time-series" ->
          new TimeSeriesWidgetSpec(
              id, compositeLabel(results), null, compositeSeries(results), "number", null);
      case "bar-chart" ->
          new BarChartWidgetSpec(
              id, compositeLabel(results), null, compositeSeries(results), "number", false, null);
      default -> throw new IllegalArgumentException("unsupported composite render type: " + type);
    };
  }

  /** Align equal weekday ranges without changing the dated semantic results or their provenance. */
  private TimeSeriesWidgetSpec weekdayComparison(String id, List<MetricResult> results) {
    var dates = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.forLanguageTag("en-AU"));
    List<Series> series =
        IntStream.range(0, results.size())
            .mapToObj(
                index -> {
                  var result = (TimeSeriesResult) results.get(index);
                  var range = result.provenance().range();
                  Map<java.time.LocalDate, BigDecimal> values = new LinkedHashMap<>();
                  result
                      .series()
                      .forEach(
                          s -> s.points().forEach(p -> values.put(p.bucketStart(), p.value())));
                  // Include absent buckets as null so a missing day never shifts the other week's
                  // values.
                  var points =
                      range
                          .from()
                          .datesUntil(range.to().plusDays(1))
                          .map(
                              date ->
                                  new Point(
                                      date.getDayOfWeek()
                                          .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                      values.get(date)))
                          .toList();
                  return new Series(
                      "period-" + index,
                      dates.format(range.from()) + " – " + dates.format(range.to()),
                      points);
                })
            .toList();
    return new TimeSeriesWidgetSpec(
        id,
        label(results.getFirst()),
        "Daily comparison by weekday. Gaps indicate unavailable values.",
        series,
        format(results.getFirst()),
        null);
  }

  private List<Series> seriesOf(TimeSeriesResult ts) {
    return ts.series().stream()
        .map(
            s ->
                new Series(
                    s.dimensionValue() == null ? "value" : s.dimensionValue(),
                    seriesLabel(ts, s),
                    s.points().stream()
                        .map(p -> new Point(p.bucketStart().toString(), p.value()))
                        .toList()))
        .toList();
  }

  private List<Series> compositeSeries(List<MetricResult> results) {
    return results.stream()
        .map(r -> (TimeSeriesResult) r)
        .flatMap(ts -> seriesOf(ts).stream())
        .toList();
  }

  private String seriesLabel(TimeSeriesResult ts, MetricSeries s) {
    String name = catalog.definition(ts.metric()).name();
    return s.dimensionValue() == null ? name : name + " · " + s.dimensionValue();
  }

  private List<Column> columnsOf(List<MetricResult> results) {
    return results.stream()
        .map(
            r ->
                new Column(
                    r.metric().value(), catalog.definition(r.metric()).name(), unitFormat(r)))
        .toList();
  }

  private List<Map<String, Object>> rowsOf(List<MetricResult> results) {
    Map<String, Object> row = new LinkedHashMap<>();
    for (MetricResult r : results) row.put(r.metric().value(), total((TimeSeriesResult) r));
    return List.of(row);
  }

  private String unitFormat(MetricResult r) {
    return switch (catalog.definition(r.metric()).unit()) {
      case "AUD" -> "currency";
      case "hours" -> "decimal";
      case "%" -> "percent";
      default -> null;
    };
  }

  private BigDecimal total(TimeSeriesResult ts) {
    BigDecimal t = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries s : ts.series())
      for (MetricPoint p : s.points())
        if (p.value() != null) {
          t = t.add(p.value());
          any = true;
        }
    return any ? t : null;
  }

  private String format(MetricResult r) {
    return switch (catalog.definition(r.metric()).unit()) {
      case "AUD" -> "currency";
      case "%" -> "percent";
      default -> "number";
    };
  }

  private String label(MetricResult r) {
    return catalog.definition(r.metric()).name();
  }

  private String compositeLabel(List<MetricResult> rs) {
    return rs.isEmpty() ? "" : label(rs.get(0));
  }

  private static String str(BigDecimal v, String unit) {
    return v == null ? null : v.toPlainString() + (unit == null ? "" : " " + unit);
  }
}
