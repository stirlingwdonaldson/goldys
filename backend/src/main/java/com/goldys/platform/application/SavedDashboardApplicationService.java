package com.goldys.platform.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedDashboardRevisionRepository;
import com.goldys.platform.dashboard.SavedDashboardShareRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.reporting.WidgetRenderer;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.widget.WidgetSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved-dashboard CRUD and rendering. Dashboards persist bounded semantic query configuration only;
 * rendering re-runs those queries through the shared {@link WidgetRenderer} (per-metric
 * authorization is added in a later batch), so a reopened dashboard always shows current resolved
 * data, never a stale snapshot.
 */
@Service
public class SavedDashboardApplicationService {
  private static final ResourceKey RESOURCE = new ResourceKey("dashboards");
  private static final String LAYOUT_GRID = "grid";
  private static final int DOCUMENT_SCHEMA_VERSION = 2;
  private static final Clock CLOCK = Clock.systemUTC();
  private static final Set<String> RENDER_TYPES =
      Set.of("stat", "time-series", "bar-chart", "table", "ranked-list");

  private final SavedDashboardRepository repository;
  private final MetricCatalog catalog;
  private final WidgetRenderer renderer;
  private final ObjectMapper mapper;
  private final PermissionService permissions;
  private final SavedDashboardRevisionRepository revisions;
  private final SavedDashboardShareRepository shares;
  private final MetricQueryService metricQueryService;

  public SavedDashboardApplicationService(
      SavedDashboardRepository repository,
      MetricCatalog catalog,
      WidgetRenderer renderer,
      ObjectMapper mapper,
      PermissionService permissions,
      SavedDashboardRevisionRepository revisions,
      SavedDashboardShareRepository shares,
      MetricQueryService metricQueryService) {
    this.repository = repository;
    this.catalog = catalog;
    this.renderer = renderer;
    this.mapper = mapper;
    this.permissions = permissions;
    this.revisions = revisions;
    this.shares = shares;
    this.metricQueryService = metricQueryService;
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
                input.filters(),
                input.visibility(),
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
        input.title(),
        input.description(),
        input.layout(),
        input.widgets(),
        input.filters(),
        input.visibility(),
        CLOCK.instant());
    return toDocument(dashboard);
  }

  @Transactional
  public void delete(UserRole role, UUID id) {
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    repository.deleteById(id);
  }

  /** Re-runs each widget's stored queries, authorizing per metric at render time. */
  public List<RenderedWidget> render(UserRole role, String email, UUID id) {
    SavedDashboard d = requireVisible(role, email, id);
    return d.widgets().stream().map(w -> renderWidget(role, w, d.filters())).toList();
  }

  private RenderedWidget renderWidget(UserRole role, SavedWidget w, DashboardFilters filters) {
    List<MetricResult> results = new ArrayList<>();
    for (MetricQuery q : w.queries()) {
      MetricQuery merged = merge(q, filters, catalog);
      String perm = catalog.definition(merged.metric()).requiredPermission();
      try {
        permissions.require(role, new ResourceKey(perm), PermissionAction.READ);
        results.add(metricQueryService.query(merged));
      } catch (AccessDeniedException e) {
        return new RenderedWidget(w.id(), null, perm);
      }
    }
    WidgetSpec spec = renderer.render(w.id(), w.renderType(), results);
    return new RenderedWidget(w.id(), spec, null);
  }

  private SavedDashboard requireVisible(UserRole role, String email, UUID id) {
    SavedDashboard d = requireDashboard(id);
    if (d.createdBy().equals(email)) return d;
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return d;
  }

  /** Dashboard-level filters merged into a widget query; the real merge lands in a later task. */
  private static MetricQuery merge(MetricQuery q, DashboardFilters f, MetricCatalog catalog) {
    return q;
  }

  private SavedDashboard requireDashboard(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No dashboard with id " + id));
  }

  private void validate(DashboardInput input) {
    if (input.title() == null || input.title().isBlank()) {
      throw new IllegalArgumentException("Dashboard title is required.");
    }
    if (input.layout() == null || !LAYOUT_GRID.equals(input.layout())) {
      throw new IllegalArgumentException("Unsupported layout: " + input.layout());
    }
    if (input.visibility() == null) {
      throw new IllegalArgumentException("Dashboard visibility is required.");
    }
    for (SavedWidget w : input.widgets()) {
      if (w.id() == null || w.id().isBlank()) {
        throw new IllegalArgumentException("Widget id is required.");
      }
      if (w.renderType() == null || !RENDER_TYPES.contains(w.renderType())) {
        throw new IllegalArgumentException("Unsupported render type: " + w.renderType());
      }
      if (w.queries().isEmpty() || w.queries().size() > 4) {
        throw new IllegalArgumentException("Widget queries must number between 1 and 4.");
      }
      for (MetricQuery q : w.queries()) {
        catalog.definition(q.metric());
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
        d.filters(),
        d.visibility(),
        d.pinned(),
        d.createdBy(),
        d.createdAt(),
        d.updatedAt());
  }

  public record DashboardInput(
      String title,
      String description,
      String layout,
      DashboardFilters filters,
      Visibility visibility,
      List<SavedWidget> widgets) {}

  public record DashboardSummary(UUID id, String title, String updatedAt) {}

  public record DashboardDocument(
      UUID id,
      int schemaVersion,
      String title,
      String description,
      String layout,
      List<SavedWidget> widgets,
      DashboardFilters filters,
      Visibility visibility,
      boolean pinned,
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {}

  /** One widget's render outcome: a resolved spec, or the metric that denied it. */
  public record RenderedWidget(String widgetId, WidgetSpec widget, String deniedResource) {}
}
