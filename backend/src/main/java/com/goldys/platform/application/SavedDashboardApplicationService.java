package com.goldys.platform.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolInput;
import com.goldys.platform.reporting.ToolRegistry;
import com.goldys.platform.widget.WidgetSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved-dashboard CRUD and rendering. Dashboards persist query configuration (tool + bounded input)
 * only; {@link #render} re-runs those queries through the tool dispatcher so a reopened dashboard
 * always shows current resolved data, never a stale snapshot.
 */
@Service
public class SavedDashboardApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("dashboards");
  private static final String LAYOUT_GRID = "grid";
  private static final int DOCUMENT_SCHEMA_VERSION = 1;
  private static final Clock CLOCK = Clock.systemUTC();

  private final SavedDashboardRepository repository;
  private final ToolRegistry toolRegistry;
  private final ToolDispatcher dispatcher;
  private final ObjectMapper mapper;
  private final PermissionService permissions;

  public SavedDashboardApplicationService(
      SavedDashboardRepository repository,
      ToolRegistry toolRegistry,
      ToolDispatcher dispatcher,
      ObjectMapper mapper,
      PermissionService permissions) {
    this.repository = repository;
    this.toolRegistry = toolRegistry;
    this.dispatcher = dispatcher;
    this.mapper = mapper;
    this.permissions = permissions;
  }

  public List<DashboardSummary> list(UserRole role) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return repository.findAllByOrderByUpdatedAtDesc().stream()
        .map(d -> new DashboardSummary(d.id(), d.title(), d.updatedAt().toString()))
        .toList();
  }

  public DashboardDocument get(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return toDocument(requireDashboard(id));
  }

  @Transactional
  public DashboardDocument create(UserRole role, String actorEmail, DashboardInput input) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    validate(input);
    SavedDashboard saved =
        repository.save(
            SavedDashboard.create(
                input.title(),
                input.description(),
                input.layout(),
                input.widgets(),
                actorEmail,
                CLOCK.instant()));
    return toDocument(saved);
  }

  @Transactional
  public DashboardDocument update(UserRole role, UUID id, DashboardInput input) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    validate(input);
    SavedDashboard dashboard = requireDashboard(id);
    dashboard.update(
        input.title(), input.description(), input.layout(), input.widgets(), CLOCK.instant());
    return toDocument(dashboard);
  }

  @Transactional
  public void delete(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    repository.deleteById(id);
  }

  /** Re-runs each widget's stored query and returns fresh widget specs. */
  public List<WidgetSpec> render(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.READ);
    SavedDashboard dashboard = requireDashboard(id);
    return dashboard.widgets().stream()
        .map(w -> dispatcher.dispatch(toolId(w.tool()), input(w), role).widget())
        .toList();
  }

  private ToolInput input(SavedWidget widget) {
    ReportingTool tool = tool(widget.tool());
    return mapper.convertValue(widget.input(), tool.inputType());
  }

  private ToolId toolId(String tool) {
    return tool(tool).id();
  }

  private ReportingTool tool(String tool) {
    try {
      ToolId id = ToolId.valueOf(tool);
      return toolRegistry
          .find(id)
          .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + tool));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown tool: " + tool, e);
    }
  }

  private SavedDashboard requireDashboard(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No dashboard with id " + id));
  }

  private static void validate(DashboardInput input) {
    if (input.title() == null || input.title().isBlank()) {
      throw new IllegalArgumentException("Dashboard title is required.");
    }
    if (input.layout() == null || !LAYOUT_GRID.equals(input.layout())) {
      throw new IllegalArgumentException("Unsupported layout: " + input.layout());
    }
    for (SavedWidget w : input.widgets()) {
      if (w.id() == null || w.id().isBlank()) {
        throw new IllegalArgumentException("Widget id is required.");
      }
      if (w.tool() == null || w.tool().isBlank()) {
        throw new IllegalArgumentException("Widget tool is required.");
      }
    }
  }

  private DashboardDocument toDocument(SavedDashboard d) {
    return new DashboardDocument(
        d.id(),
        DOCUMENT_SCHEMA_VERSION,
        d.title(),
        d.description(),
        d.layout(),
        d.widgets(),
        d.createdBy(),
        d.createdAt(),
        d.updatedAt());
  }

  public record DashboardInput(
      String title, String description, String layout, List<SavedWidget> widgets) {}

  public record DashboardSummary(UUID id, String title, String updatedAt) {}

  public record DashboardDocument(
      UUID id,
      int schemaVersion,
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {}
}
