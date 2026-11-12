package com.goldys.platform.reporting;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.ComparisonService;
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Compares a metric over the requested range against a reference period derived by {@link
 * ComparisonService} (previous week, same period last year, ...), rendering both series and a
 * delta.
 */
@Component
public class CompareMetricPeriodsTool extends MetricReportingTool {
  private final ComparisonService comparisons;

  public CompareMetricPeriodsTool(
      MetricQueryService metrics, WidgetRenderer renderer, ComparisonService comparisons) {
    super(metrics, renderer);
    this.comparisons = comparisons;
  }

  @Override
  public ToolId id() {
    return ToolId.COMPARE_METRIC_PERIODS;
  }

  @Override
  public String name() {
    return "compare_metric_periods";
  }

  @Override
  public String description() {
    return "Compare a metric between a current date range and a reference period (previous day/week, "
        + "same period last year, rolling windows), returning both series and the percentage delta.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return CompareMetricPeriodsInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("conversational.chat");
  }

  @Override
  protected String renderType(List<MetricResult> results) {
    return "bar-chart";
  }

  @Override
  protected List<String> additionalNotices(List<MetricQuery> queries, List<MetricResult> results) {
    BigDecimal delta =
        ComparisonService.deltaPercent(
            totalOf((TimeSeriesResult) results.get(0)), totalOf((TimeSeriesResult) results.get(1)));
    return List.of(
        "vs "
            + queries.get(0).comparison()
            + (delta == null
                ? ""
                : ": " + delta.movePointRight(2).setScale(1, RoundingMode.HALF_UP) + "%"));
  }

  @Override
  public List<MetricQuery> toMetricQueries(ToolInput input) {
    CompareMetricPeriodsInput in = require(input);
    if (in.comparison() == Comparison.BUDGET || in.comparison() == Comparison.FORECAST) {
      throw new IllegalArgumentException("no data source yet for " + in.comparison());
    }
    MetricQuery current = currentQuery(in);
    MetricQuery reference =
        new MetricQuery(
            in.metric(), comparisons.referenceRange(current), in.grain(), Set.of(), null);
    return List.of(current, reference);
  }

  private static MetricQuery currentQuery(CompareMetricPeriodsInput in) {
    return new MetricQuery(
        in.metric(),
        new TimeRange(in.startDate(), in.endDate(), Calendar.CALENDAR),
        in.grain(),
        Set.of(),
        in.comparison());
  }

  private static CompareMetricPeriodsInput require(ToolInput input) {
    if (!(input instanceof CompareMetricPeriodsInput in)) {
      throw new IllegalArgumentException(
          "Expected CompareMetricPeriodsInput, got " + input.getClass().getSimpleName());
    }
    return in;
  }

  /** Sums the non-null points of an ungrouped time-series; null when it holds no values. */
  private static BigDecimal totalOf(TimeSeriesResult ts) {
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (MetricSeries s : ts.series()) {
      for (MetricPoint p : s.points()) {
        if (p.value() != null) {
          total = total.add(p.value());
          any = true;
        }
      }
    }
    return any ? total : null;
  }
}
