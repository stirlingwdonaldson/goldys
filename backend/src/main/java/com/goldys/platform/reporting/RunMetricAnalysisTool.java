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

  public RunMetricAnalysisTool(MetricCatalog catalog, MetricQueryService metrics) {
    this.catalog = catalog;
    this.metrics = metrics;
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
        + "Use for multiple metrics or periods. Use TOTAL for period totals; do not sum daily ratios. "
        + "Returns individual values with scope, units, missing status and provenance, never inferred joins. "
        + "Use compare_metric_periods for calculated percentage changes and get_metric for top sellers. "
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
    // Validate the entire plan first; a later unsupported query must not cause a partial read.
    for (MetricQuery query : plan) {
      catalog.validateQuery(query);
      if (query.metric() == MetricId.PRODUCT_TOP_SELLERS) {
        throw new IllegalArgumentException(
            "Use get_metric for product.top_sellers; analysis requires time-series metrics");
      }
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
                "from",
                "to",
                "grain",
                "dimensions",
                "group",
                "bucket",
                "value",
                "unit",
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
    row.put("unit", catalog.definition(query.metric()).unit());
    row.put("status", point.status() == null ? null : point.status().name());
    return row;
  }
}
