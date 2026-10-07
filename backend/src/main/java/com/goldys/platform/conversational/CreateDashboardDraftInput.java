package com.goldys.platform.conversational;

import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.reporting.ToolInput;
import java.util.List;
import java.util.UUID;

/**
 * The typed, schema-bounded input for {@link ToolId#CREATE_DASHBOARD_DRAFT}. Spring AI derives the
 * tool's JSON schema from this record, so the model can only emit valid {@code MetricId}, {@code
 * Dimension}, {@code Comparison} and {@code TimeGrain} enum values. The {@code renderType} string
 * is validated against the metric catalogue by {@link CreateDashboardDraftTool}.
 *
 * <p>An optional {@code dashboardId} selects update mode: a null id proposes a new dashboard
 * ({@code POST /api/dashboards}), while a non-null id proposes changes to that existing dashboard
 * ({@code PUT /api/dashboards/{id}}). It is carried metadata only — the draft is never persisted.
 */
public record CreateDashboardDraftInput(
    String title,
    String description,
    DashboardFilters filters,
    List<SavedWidget> widgets,
    UUID dashboardId)
    implements ToolInput {
  public CreateDashboardDraftInput {
    filters = filters == null ? DashboardFilters.empty() : filters;
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
  }

  /** Convenience constructor for a create-mode draft (no existing dashboard id). */
  public CreateDashboardDraftInput(
      String title, String description, DashboardFilters filters, List<SavedWidget> widgets) {
    this(title, description, filters, widgets, null);
  }
}
