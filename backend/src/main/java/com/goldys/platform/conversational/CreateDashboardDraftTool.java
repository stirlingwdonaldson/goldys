package com.goldys.platform.conversational;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardWidgetValidator;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.widget.StatWidgetSpec;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The declarative dashboard-draft tool: the model proposes a saved dashboard as a typed record, and
 * this tool validates the proposal against the {@link DashboardWidgetValidator} — every metric
 * known, every dimension within that metric's {@code validDimensions}, and the {@code renderType}
 * supported by the metric. It never persists: the validated {@link DashboardDraft} is carried on
 * the {@link AnswerPayload} for the frontend to render and confirm.
 */
@Component
public class CreateDashboardDraftTool implements ReportingTool {
  private final DashboardWidgetValidator widgetValidator;

  public CreateDashboardDraftTool(DashboardWidgetValidator widgetValidator) {
    this.widgetValidator = widgetValidator;
  }

  @Override
  public ToolId id() {
    return ToolId.CREATE_DASHBOARD_DRAFT;
  }

  @Override
  public String name() {
    return "create_dashboard_draft";
  }

  @Override
  public String description() {
    return "Propose a saved dashboard draft: a title, an optional description, optional "
        + "dashboard-level filters, and an ordered list of widgets. Each widget names a "
        + "renderType, one or more metric queries (metric, date range, grain, dimensions, "
        + "comparison) and a grid layout. renderType must be lowercase: stat, time-series, bar-chart, table, or ranked-list. "
        + "A net-sales total widget example is {id:'net',renderType:'stat',layout:{w:4,h:2},"
        + "queries:[{metric:'sales.net',range:{from:'2026-11-02',to:'2026-11-08',calendar:'TRADING'},"
        + "grain:'TOTAL',dimensions:[]}]}. Filters use {dateRange:{from,to,calendar},dimensions:[]}. "
        + "Omit unused optional fields instead of sending null. Use the requested dates. Never use KPI or LINE as renderType. The draft is validated but not saved.";
  }

  @Override
  public Class<? extends ToolInput> inputType() {
    return CreateDashboardDraftInput.class;
  }

  @Override
  public ResourceKey resource() {
    return new ResourceKey("dashboards");
  }

  @Override
  public java.util.Set<com.goldys.platform.semantic.catalog.MetricId> referencedMetrics(
      ToolInput input) {
    if (!(input instanceof CreateDashboardDraftInput draft)) {
      throw new IllegalArgumentException("Expected CreateDashboardDraftInput");
    }
    // Draft metadata is governed too; no query executes merely to authorize the proposal.
    return draft.widgets().stream()
        .flatMap(widget -> widget.queries().stream())
        .map(com.goldys.platform.semantic.catalog.MetricQuery::metric)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  @Override
  public ToolResult execute(ToolInput input, UserRole role) {
    DashboardDraft draft = toDraft(input);
    StatWidgetSpec widget =
        new StatWidgetSpec(
            UUID.randomUUID().toString(),
            "Dashboard draft ready",
            "Draft \""
                + draft.title()
                + "\" with "
                + draft.widgets().size()
                + " widget(s) is ready"
                + " — confirm to save.",
            null,
            null,
            null,
            null);
    return new ToolResult(widget, List.of(), List.of(), List.of());
  }

  /** Casts, validates, and returns the non-persisted draft document. */
  public DashboardDraft toDraft(ToolInput input) {
    if (!(input instanceof CreateDashboardDraftInput in)) {
      throw new IllegalArgumentException(
          "Expected CreateDashboardDraftInput, got " + input.getClass().getSimpleName());
    }
    validate(in);
    return new DashboardDraft(
        in.title(), in.description(), in.filters(), in.widgets(), in.dashboardId());
  }

  /**
   * Validates a draft against the metric catalogue; throws {@link IllegalArgumentException} on
   * invalid input.
   */
  public void validate(CreateDashboardDraftInput input) {
    if (input.title() == null || input.title().isBlank()) {
      throw new IllegalArgumentException("Dashboard title is required.");
    }
    for (SavedWidget widget : input.widgets()) {
      widgetValidator.validate(widget);
    }
  }
}
