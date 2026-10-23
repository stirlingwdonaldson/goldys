package com.goldys.platform.dashboard;

import com.goldys.platform.semantic.catalog.MetricQuery;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One persisted widget: a bounded set of semantic queries plus a rendering type and a grid span.
 * Never generated code, never free-form SQL/filters.
 *
 * <p>A {@code savedQueryId} (with {@code renderType "query"}) embeds a saved relational query; the
 * dashboard render path re-runs and re-authorizes it. Metric widgets leave it null.
 */
public record SavedWidget(
    String id,
    String renderType,
    List<MetricQuery> queries,
    WidgetLayout layout,
    UUID savedQueryId) {
  public SavedWidget {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(renderType, "renderType");
    queries = queries == null ? List.of() : List.copyOf(queries);
    Objects.requireNonNull(layout, "layout");
  }

  /** Compatibility constructor: a metric widget carries no saved query. */
  public SavedWidget(String id, String renderType, List<MetricQuery> queries, WidgetLayout layout) {
    this(id, renderType, queries, layout, null);
  }
}
