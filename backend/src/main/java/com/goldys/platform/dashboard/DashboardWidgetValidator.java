package com.goldys.platform.dashboard;

import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricDefinition;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for whether a widget's {@code renderType}↔metric mapping and
 * per-metric dimensions are valid. Both the saved-dashboard service and the AI draft tool route
 * through here, so a widget that validates in one path can never 500 at render time in the other.
 *
 * <p>The contract mirrors {@link com.goldys.platform.reporting.WidgetRenderer}: a ranked-list
 * result ({@link MetricId#PRODUCT_TOP_SELLERS}) renders only as {@code "ranked-list"} and only as a
 * single query; {@code "stat"} and {@code "ranked-list"} are single-query only; composite
 * (multi-query) widgets render as {@code "time-series"}, {@code "bar-chart"} or {@code "table"}.
 */
@Component
public class DashboardWidgetValidator {
  private static final Set<String> RENDER_TYPES =
      Set.of("stat", "time-series", "bar-chart", "table", "ranked-list");
  private static final Set<String> SINGLE_TIME_SERIES_RENDER_TYPES =
      Set.of("stat", "time-series", "bar-chart", "table");
  private static final Set<String> COMPOSITE_RENDER_TYPES =
      Set.of("time-series", "bar-chart", "table");
  private static final String RANKED_LIST = "ranked-list";

  private final MetricCatalog catalog;

  public DashboardWidgetValidator(MetricCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Validates a widget's render type, query count, renderType↔metric mapping and per-metric
   * dimensions, throwing {@link IllegalArgumentException} on the first violation.
   */
  public void validate(SavedWidget widget) {
    if (widget.renderType() == null || !RENDER_TYPES.contains(widget.renderType())) {
      throw new IllegalArgumentException("Unsupported render type: " + widget.renderType());
    }
    List<MetricQuery> queries = widget.queries();
    if (queries.isEmpty() || queries.size() > 4) {
      throw new IllegalArgumentException("Widget queries must number between 1 and 4.");
    }
    boolean anyRanked = queries.stream().anyMatch(q -> q.metric() == MetricId.PRODUCT_TOP_SELLERS);
    if (anyRanked) {
      if (queries.size() != 1 || !RANKED_LIST.equals(widget.renderType())) {
        throw new IllegalArgumentException(
            "A ranked-list metric requires a single query rendered as 'ranked-list'.");
      }
    } else if (queries.size() == 1) {
      if (!SINGLE_TIME_SERIES_RENDER_TYPES.contains(widget.renderType())) {
        throw new IllegalArgumentException(
            "Render type " + widget.renderType() + " is not supported for this metric.");
      }
    } else if (!COMPOSITE_RENDER_TYPES.contains(widget.renderType())) {
      throw new IllegalArgumentException(
          "Render type " + widget.renderType() + " is not supported for a composite widget.");
    }
    for (MetricQuery query : queries) {
      MetricDefinition definition = catalog.definition(query.metric());
      for (Dimension dimension : query.dimensions()) {
        if (!definition.validDimensions().contains(dimension)) {
          throw new IllegalArgumentException(
              "Dimension " + dimension + " is not valid for metric " + query.metric().value());
        }
      }
    }
  }
}
