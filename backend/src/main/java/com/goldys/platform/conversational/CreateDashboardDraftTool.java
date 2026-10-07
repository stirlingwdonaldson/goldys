package com.goldys.platform.conversational;

import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricDefinition;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.widget.StatWidgetSpec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The declarative dashboard-draft tool: the model proposes a saved dashboard as a typed record, and
 * this tool validates the proposal against the {@link MetricCatalog} — every metric known, every
 * dimension within that metric's {@code validDimensions}, and the {@code renderType} supported by
 * the metric. It never persists: the validated {@link DashboardDraft} is carried on the {@link
 * AnswerPayload} for the frontend to render and confirm.
 */
@Component
public class CreateDashboardDraftTool implements ReportingTool {
  private static final Set<String> TIME_SERIES_RENDER_TYPES =
      Set.of("stat", "time-series", "bar-chart", "table");
  private static final String RANKED_LIST = "ranked-list";

  private final MetricCatalog catalog;

  public CreateDashboardDraftTool(MetricCatalog catalog) {
    this.catalog = catalog;
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
        + "comparison) and a grid layout. The draft is validated but not saved.";
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
    return new ToolResult(widget, List.of());
  }

  /** Casts, validates, and returns the non-persisted draft document. */
  public DashboardDraft toDraft(ToolInput input) {
    if (!(input instanceof CreateDashboardDraftInput in)) {
      throw new IllegalArgumentException(
          "Expected CreateDashboardDraftInput, got " + input.getClass().getSimpleName());
    }
    validate(in);
    return new DashboardDraft(in.title(), in.description(), in.filters(), in.widgets());
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
      if (widget.renderType() == null || !isRenderTypeAllowed(widget)) {
        throw new IllegalArgumentException("Unsupported render type: " + widget.renderType());
      }
      if (widget.queries().isEmpty() || widget.queries().size() > 4) {
        throw new IllegalArgumentException("Widget queries must number between 1 and 4.");
      }
      for (MetricQuery query : widget.queries()) {
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

  private boolean isRenderTypeAllowed(SavedWidget widget) {
    boolean ranked =
        widget.queries().stream().anyMatch(q -> q.metric() == MetricId.PRODUCT_TOP_SELLERS);
    if (ranked) {
      return RANKED_LIST.equals(widget.renderType());
    }
    return TIME_SERIES_RENDER_TYPES.contains(widget.renderType());
  }
}
