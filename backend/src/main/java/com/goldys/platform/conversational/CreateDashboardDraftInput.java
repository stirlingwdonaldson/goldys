package com.goldys.platform.conversational;

import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.reporting.ToolInput;
import java.util.List;

/**
 * The typed, schema-bounded input for {@link ToolId#CREATE_DASHBOARD_DRAFT}. Spring AI derives the
 * tool's JSON schema from this record, so the model can only emit valid {@code MetricId}, {@code
 * Dimension}, {@code Comparison} and {@code TimeGrain} enum values. The {@code renderType} string
 * is validated against the metric catalogue by {@link CreateDashboardDraftTool}.
 */
public record CreateDashboardDraftInput(
    String title, String description, DashboardFilters filters, List<SavedWidget> widgets)
    implements ToolInput {
  public CreateDashboardDraftInput {
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
  }
}
