package com.goldys.platform.reporting;

import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catalogue tools prepare their reads once. The dispatcher authorizes that immutable list and
 * passes the same list to execution; presentation receives results, never a query service.
 */
public abstract class MetricReportingTool implements ReportingTool {
  private final MetricQueryService metrics;
  private final WidgetRenderer renderer;

  protected MetricReportingTool(MetricQueryService metrics, WidgetRenderer renderer) {
    this.metrics = metrics;
    this.renderer = renderer;
  }

  @Override
  public abstract List<MetricQuery> toMetricQueries(ToolInput input);

  @Override
  public final ToolResult execute(ToolInput input, UserRole role) {
    return executePrepared(List.copyOf(toMetricQueries(input)));
  }

  final ToolResult executePrepared(List<MetricQuery> queries) {
    List<MetricResult> results = queries.stream().map(metrics::query).toList();
    List<String> notices = new ArrayList<>();
    results.forEach(result -> notices.addAll(result.notices()));
    notices.addAll(additionalNotices(queries, results));
    return new ToolResult(
        renderer.render(UUID.randomUUID().toString(), renderType(results), results),
        notices,
        results.stream().map(MetricResult::provenance).toList(),
        relatedMetrics(results));
  }

  protected abstract String renderType(List<MetricResult> results);

  protected List<String> additionalNotices(List<MetricQuery> queries, List<MetricResult> results) {
    return List.of();
  }

  protected List<MetricId> relatedMetrics(List<MetricResult> results) {
    return List.of();
  }
}
