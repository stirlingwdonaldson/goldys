package com.goldys.platform.dashboard;

import com.goldys.platform.semantic.catalog.MetricQuery;
import java.util.List;
import java.util.Objects;

/**
 * One persisted widget: a bounded set of semantic queries plus a rendering type and a grid span.
 * Never generated code, never free-form SQL/filters.
 */
public record SavedWidget(
    String id, String renderType, List<MetricQuery> queries, WidgetLayout layout) {
  public SavedWidget {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(renderType, "renderType");
    queries = queries == null ? List.of() : List.copyOf(queries);
    Objects.requireNonNull(layout, "layout");
  }
}
