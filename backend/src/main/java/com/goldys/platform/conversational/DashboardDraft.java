package com.goldys.platform.conversational;

import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedWidget;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A validated-but-<em>not</em>-persisted dashboard proposal produced by the declarative draft tool.
 * It carries only the fields a user confirms before saving: title, description, filters and
 * widgets. The id, visibility, created-by and revision metadata are assigned later by {@code POST
 * /api/dashboards} on confirm.
 *
 * <p>A non-null {@code dashboardId} marks an update proposal for an existing dashboard, which the
 * frontend confirms via {@code PUT /api/dashboards/{id}}; a null id is a create proposal.
 */
public record DashboardDraft(
    String title,
    String description,
    DashboardFilters filters,
    List<SavedWidget> widgets,
    UUID dashboardId) {
  public DashboardDraft {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(filters, "filters");
    widgets = widgets == null ? List.of() : List.copyOf(widgets);
  }
}
