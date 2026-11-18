package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.Column;
import com.goldys.platform.widget.TableWidgetSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Executes a bounded plan using the same catalogue and calculations as dashboard metric queries.
 */
@Component
public class RunMetricAnalysisTool implements ReportingTool {
  private final MetricCatalog catalog;
  private final MetricQueryService metrics;
  private final MetricChartRenderer charts;

  public RunMetricAnalysisTool(MetricCatalog catalog, MetricQueryService metrics) {
    this.catalog = catalog;
    this.metrics = metrics;
    this.charts = new MetricChartRenderer(catalog);
  }

  @Override
  public ToolId id() {
    return ToolId.RUN_METRIC_ANALYSIS;
  }

  @Override
  public String name() {
    return "run_metric_analysis";
  }

  @Override
  public String description() {
    return "Execute 1..6 approved metric queries together, each with explicit dates, grain and dimensions. "
        + "Preferred for numeric answers: one query for one metric, or several for multiple metrics or periods. Use TOTAL for period totals; do not sum daily ratios. "
        + "Returns individual values with scope, units, missing status and provenance, never inferred joins. "
        + "For EVERY answer consider whether a chart helps reveal a trend, comparison, distribution or composition. "
        + "Set optional chart to render a supporting graph inline: {kind:LINE|AREA|BAR|PIE|DONUT|RADAR|RADIAL, "
        + "stacked:false, horizontal:false, alignment:DATE|WEEKDAY|PERIOD}. "
        + "LINE: trends/comparing periods; AREA: magnitude over time; BAR: categories/rankings (horizontal for long names); "
        + "PIE/DONUT: a small complete nonnegative additive partition from ONE query; RADAR: 3..12 comparable categories; "
        + "RADIAL: small nonnegative magnitude comparisons, never an invented target. "
        + "Stack only additive groups from one query; charts cannot mix units, silently omit categories or fill gaps. "
        + "Use DAY/WEEK/MONTH for trends and TOTAL for category comparisons. "
        + "Use WEEKDAY to overlay matching <=7-day DAY ranges, or PERIOD for equal-length DAY ranges; dates remain in legend labels. "
        + "Omit chart for a lone value, prose-only answers, unavailable data or unsuitable comparisons. "
        + "If a chart is rejected, correct the query/chart or explain the limitation; do not invent values. "
        + "Use compare_metric_periods for calculated percentage changes; product.top_sellers supports a single-query BAR chart. "
        + "Filters, new formulas and cross-dataset joins are not supported by this plan.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return RunMetricAnalysisInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("conversational.chat");
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    if (!(input instanceof RunMetricAnalysisInput in))
      throw new IllegalArgumentException("Expected RunMetricAnalysisInput");
    return in.queries().stream()
        .map(
            q ->
                new MetricQuery(
                    q.metric(),
                    new TimeRange(q.startDate(), q.endDate(), Calendar.CALENDAR),
                    q.grain(),
                    q.dimensions(),
                    null))
        .toList();
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    List<MetricQuery> plan = toMetricQueries(input);
    ChartOptions chart = ((RunMetricAnalysisInput) input).chart();
    if (chart != null) charts.validatePlan(plan, chart);
    // Validate the entire plan first; a later unsupported query must not cause a partial read.
    for (MetricQuery query : plan) {
      catalog.validateQuery(query);
      if (chart == null && query.metric() == MetricId.PRODUCT_TOP_SELLERS) {
        throw new IllegalArgumentException(
            "Use get_metric for product.top_sellers; analysis requires time-series metrics");
      }
    }
    if (chart != null) {
      var results = plan.stream().map(metrics::query).toList();
      return new ToolResult(
          charts.render(plan, results, chart),
          results.stream().flatMap(result -> result.notices().stream()).toList(),
          results.stream()
              .map(com.goldys.platform.semantic.catalog.MetricResult::provenance)
              .toList(),
          List.of());
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    List<MetricProvenance> provenance = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (int index = 0; index < plan.size(); index++) {
      MetricQuery query = plan.get(index);
      TimeSeriesResult result = (TimeSeriesResult) metrics.query(query);
      provenance.add(result.provenance());
      int number = index + 1;
      result.notices().forEach(notice -> notices.add("Query " + number + ": " + notice));
      for (MetricSeries series : result.series()) {
        for (MetricPoint point : series.points()) rows.add(row(number, query, series, point));
      }
    }
    List<Column> columns =
        List.of(
                "query",
                "metric",
                "definition",
                "from",
                "to",
                "grain",
                "dimensions",
                "group",
                "bucket",
                "value",
                "unit",
                "displayValue",
                "status")
            .stream()
            .map(key -> new Column(key, key, null))
            .toList();
    return new ToolResult(
        new TableWidgetSpec(
            UUID.randomUUID().toString(),
            "Metric analysis",
            "Each row retains its requested scope; values are not combined across metrics or periods",
            columns,
            rows,
            null),
        notices,
        provenance,
        List.of());
  }

  private Map<String, Object> row(
      int number, MetricQuery query, MetricSeries series, MetricPoint point) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("query", number);
    row.put("metric", query.metric().value());
    row.put("definition", catalog.definition(query.metric()).definition());
    row.put("from", query.range().from().toString());
    row.put("to", query.range().to().toString());
    row.put("grain", query.grain().name());
    row.put(
        "dimensions",
        query.dimensions().stream()
            .sorted()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.joining(", ")));
    row.put("group", series.dimensionValue());
    row.put("bucket", point.bucketStart().toString());
    row.put("value", point.value());
    String unit = catalog.definition(query.metric()).unit();
    row.put("unit", unit);
    // Catalogue percentages are stored as ratios; format them here, never in model arithmetic.
    row.put(
        "displayValue",
        point.value() == null
            ? null
            : "%".equals(unit)
                ? point
                        .value()
                        .movePointRight(2)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                        .toPlainString()
                    + "%"
                : point.value().toPlainString() + " " + unit);
    row.put("status", point.status() == null ? null : point.status().name());
    return row;
  }
}
