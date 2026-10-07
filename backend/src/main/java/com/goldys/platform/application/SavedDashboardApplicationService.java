package com.goldys.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.DashboardTemplate;
import com.goldys.platform.dashboard.DashboardTemplateCatalog;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedDashboardRevision;
import com.goldys.platform.dashboard.SavedDashboardRevisionRepository;
import com.goldys.platform.dashboard.SavedDashboardShare;
import com.goldys.platform.dashboard.SavedDashboardShareRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.reporting.WidgetRenderer;
import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricResult;
import com.goldys.platform.semantic.catalog.TimeRange;
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
 * Saved-dashboard CRUD, rendering, versioning and sharing. Dashboards persist bounded semantic
 * query configuration only; rendering re-runs those queries through the shared {@link
 * WidgetRenderer} with per-metric authorization at render time, so a reopened dashboard always
 * shows current resolved data, never a stale snapshot, and a denied metric renders an explicit
 * denial.
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
  private final SavedDashboardRevisionRepository revisionsRepository;
  private final SavedDashboardShareRepository shares;
  private final MetricQueryService metricQueryService;
  private final DashboardTemplateCatalog templateCatalog;

  public SavedDashboardApplicationService(
      SavedDashboardRepository repository,
      MetricCatalog catalog,
      WidgetRenderer renderer,
      ObjectMapper mapper,
      PermissionService permissions,
      SavedDashboardRevisionRepository revisionsRepository,
      SavedDashboardShareRepository shares,
      MetricQueryService metricQueryService,
      DashboardTemplateCatalog templateCatalog) {
    this.repository = repository;
    this.catalog = catalog;
    this.renderer = renderer;
    this.mapper = mapper;
    this.permissions = permissions;
    this.revisionsRepository = revisionsRepository;
    this.shares = shares;
    this.metricQueryService = metricQueryService;
    this.templateCatalog = templateCatalog;
  }

  public List<DashboardSummary> list(UserRole role, String email) {
    return repository.findAllByOrderByUpdatedAtDesc().stream()
        .filter(d -> isVisible(role, email, d))
        .map(d -> new DashboardSummary(d.id(), d.title(), d.updatedAt().toString()))
        .toList();
  }

  public DashboardDocument get(UserRole role, String email, UUID id) {
    return toDocument(requireVisible(role, email, id));
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
    writeRevision(saved, actorEmail);
    return toDocument(saved);
  }

  /** The nine code-based starting-point templates. */
  public List<DashboardTemplate> templates() {
    return templateCatalog.templates();
  }

  /** Copies a template into a new PRIVATE dashboard owned by the caller, via the normal create path. */
  @Transactional
  public DashboardDocument instantiate(UserRole role, String email, String templateId) {
    DashboardTemplate template = templateCatalog.byId(templateId);
    return create(
        role,
        email,
        new DashboardInput(
            template.name(),
            template.description(),
            LAYOUT_GRID,
            DashboardFilters.empty(),
            Visibility.PRIVATE,
            template.widgets()));
  }

  @Transactional
  public DashboardDocument update(UserRole role, String email, UUID id, DashboardInput input) {
    SavedDashboard dashboard = requireEditable(role, email, id);
    validate(input);
    dashboard.update(
        input.title(),
        input.description(),
        input.layout(),
        input.widgets(),
        input.filters(),
        input.visibility(),
        CLOCK.instant());
    dashboard.incrementRevision();
    writeRevision(dashboard, email);
    return toDocument(dashboard);
  }

  public List<DashboardRevisionSummary> revisions(UUID id) {
    return revisionsRepository.findByDashboardIdOrderByRevisionDesc(id).stream()
        .map(r -> new DashboardRevisionSummary(r.revision(), r.createdBy(), r.createdAt()))
        .toList();
  }

  @Transactional
  public DashboardDocument restore(UserRole role, String email, UUID id, int revision) {
    requireEditable(role, email, id);
    SavedDashboardRevision rev = findRevision(id, revision);
    DashboardDocument doc = readDocument(rev.document());
    DashboardInput input =
        new DashboardInput(
            doc.title(),
            doc.description(),
            doc.layout(),
            doc.filters(),
            doc.visibility(),
            doc.widgets());
    return update(role, email, id, input);
  }

  @Transactional
  public void delete(UserRole role, String email, UUID id) {
    requireEditable(role, email, id);
    repository.deleteById(id);
  }

  @Transactional
  public void setSharing(
      UserRole role, String email, UUID id, Visibility visibility, List<UserRole> roles) {
    SavedDashboard dashboard = requireEditable(role, email, id);
    if (visibility == null) {
      throw new IllegalArgumentException("Dashboard visibility is required.");
    }
    dashboard.setVisibility(visibility);
    shares.deleteAll(shares.findByDashboardId(id));
    for (UserRole r : roles) {
      shares.save(SavedDashboardShare.create(id, r.department().value(), r.seniority().value()));
    }
  }

  public List<UserRole> sharing(UUID id) {
    return shares.findByDashboardId(id).stream()
        .map(
            s -> new UserRole(new DepartmentCode(s.department()), new SeniorityCode(s.seniority())))
        .toList();
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
    switch (d.visibility()) {
      case PRIVATE -> throw AccessDeniedException.forResource("dashboard");
      case SHARED -> {
        if (!isSharedWith(role, id)) throw AccessDeniedException.forResource("dashboard");
      }
      case ORG_WIDE -> {
        /* fall through to read check below */
      }
    }
    permissions.require(role, RESOURCE, PermissionAction.READ);
    return d;
  }

  private SavedDashboard requireEditable(UserRole role, String email, UUID id) {
    SavedDashboard d = requireDashboard(id);
    if (d.createdBy().equals(email)) return d;
    permissions.require(role, RESOURCE, PermissionAction.WRITE);
    return d;
  }

  private boolean isVisible(UserRole role, String email, SavedDashboard d) {
    if (d.createdBy().equals(email)) return true;
    return switch (d.visibility()) {
      case PRIVATE -> false;
      case SHARED -> isSharedWith(role, d.id()) && canRead(role);
      case ORG_WIDE -> canRead(role);
    };
  }

  private boolean isSharedWith(UserRole role, UUID id) {
    return shares.findByDashboardId(id).stream()
        .anyMatch(
            s ->
                s.department().equals(role.department().value())
                    && s.seniority().equals(role.seniority().value()));
  }

  private boolean canRead(UserRole role) {
    try {
      permissions.require(role, RESOURCE, PermissionAction.READ);
      return true;
    } catch (AccessDeniedException e) {
      return false;
    }
  }

  /** Dashboard-level filters merged into a widget query, gated by the metric's valid dimensions. */
  static MetricQuery merge(MetricQuery q, DashboardFilters f, MetricCatalog catalog) {
    TimeRange range = f.dateRange() != null ? f.dateRange() : q.range();
    Comparison comparison = f.comparison() != null ? f.comparison() : q.comparison();
    Set<Dimension> valid = catalog.definition(q.metric()).validDimensions();
    Set<Dimension> dims = new java.util.LinkedHashSet<>(q.dimensions());
    dims.addAll(f.dimensions());
    dims.retainAll(valid);
    return new MetricQuery(q.metric(), range, q.grain(), dims, comparison);
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

  private void writeRevision(SavedDashboard saved, String actorEmail) {
    revisionsRepository.save(
        SavedDashboardRevision.create(
            saved.id(), saved.currentRevision(), serialize(saved), actorEmail, CLOCK.instant()));
  }

  private SavedDashboardRevision findRevision(UUID id, int revision) {
    return revisionsRepository.findByDashboardIdOrderByRevisionDesc(id).stream()
        .filter(r -> r.revision() == revision)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No revision " + revision + " for dashboard " + id));
  }

  private String serialize(SavedDashboard saved) {
    try {
      return mapper.writeValueAsString(toDocument(saved));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize dashboard " + saved.id(), e);
    }
  }

  private DashboardDocument readDocument(String json) {
    try {
      return mapper.readValue(json, DashboardDocument.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize dashboard revision", e);
    }
  }

  public record DashboardInput(
      String title,
      String description,
      String layout,
      DashboardFilters filters,
      Visibility visibility,
      List<SavedWidget> widgets) {}

  public record DashboardSummary(UUID id, String title, String updatedAt) {}

  public record DashboardRevisionSummary(int revision, String createdBy, Instant createdAt) {}

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
