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
import com.goldys.platform.dashboard.DashboardWidgetValidator;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedDashboardRevision;
import com.goldys.platform.dashboard.SavedDashboardRevisionRepository;
import com.goldys.platform.dashboard.SavedDashboardShare;
import com.goldys.platform.dashboard.SavedDashboardShareRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.reporting.WidgetRenderer;
import com.goldys.platform.semantic.FreshnessState;
import com.goldys.platform.semantic.TrustState;
import com.goldys.platform.semantic.TrustSummary;
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

  private final SavedDashboardRepository repository;
  private final MetricCatalog catalog;
  private final DashboardWidgetValidator widgetValidator;
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
      DashboardWidgetValidator widgetValidator,
      WidgetRenderer renderer,
      ObjectMapper mapper,
      PermissionService permissions,
      SavedDashboardRevisionRepository revisionsRepository,
      SavedDashboardShareRepository shares,
      MetricQueryService metricQueryService,
      DashboardTemplateCatalog templateCatalog) {
    this.repository = repository;
    this.catalog = catalog;
    this.widgetValidator = widgetValidator;
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
        .map(
            d ->
                new DashboardSummary(
                    d.id(),
                    d.title(),
                    d.description(),
                    d.createdBy(),
                    d.pinned(),
                    d.visibility().name(),
                    d.updatedAt().toString()))
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

  /**
   * Copies a template into a new PRIVATE dashboard owned by the caller, via the normal create path.
   */
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

  public List<DashboardRevisionSummary> revisions(UserRole role, String email, UUID id) {
    requireVisible(role, email, id);
    return revisionsRepository.findByDashboardIdOrderByRevisionDesc(id).stream()
        .map(r -> new DashboardRevisionSummary(r.revision(), r.createdBy(), r.createdAt()))
        .toList();
  }

  @Transactional
  public DashboardDocument restore(UserRole role, String email, UUID id, int revision) {
    requireEditable(role, email, id);
    SavedDashboardRevision rev = findRevision(id, revision);
    RevisionSnapshot snapshot = readSnapshot(rev.document());
    DashboardDocument doc = snapshot.document();
    DashboardInput input =
        new DashboardInput(
            doc.title(),
            doc.description(),
            doc.layout(),
            doc.filters(),
            doc.visibility(),
            doc.widgets());
    // Replace the dashboard's share rows with the snapshot's BEFORE re-running update, so the new
    // revision written by update captures the restored shares rather than the pre-restore ones.
    shares.deleteAll(shares.findByDashboardId(id));
    for (ShareSnapshot s : snapshot.shares()) {
      shares.save(SavedDashboardShare.create(id, s.department(), s.seniority()));
    }
    return update(role, email, id, input);
  }

  @Transactional
  public void delete(UserRole role, String email, UUID id) {
    requireEditable(role, email, id);
    repository.deleteById(id);
  }

  @Transactional
  public DashboardSharing setSharing(
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
    return new DashboardSharing(visibility, roles);
  }

  public DashboardSharing sharing(UserRole role, String email, UUID id) {
    SavedDashboard d = requireVisible(role, email, id);
    List<UserRole> roles =
        shares.findByDashboardId(id).stream()
            .map(
                s ->
                    new UserRole(
                        new DepartmentCode(s.department()), new SeniorityCode(s.seniority())))
            .toList();
    return new DashboardSharing(d.visibility(), roles);
  }

  /** Toggles the pinned (favourite) flag and returns the updated document. */
  @Transactional
  public DashboardDocument pin(UserRole role, String email, UUID id) {
    SavedDashboard d = requireEditable(role, email, id);
    d.setPinned(!d.pinned());
    repository.save(d);
    return toDocument(d);
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
        return new RenderedWidget(w.id(), null, perm, null);
      }
    }
    WidgetSpec spec = renderer.render(w.id(), w.renderType(), results);
    TrustSummary trust = aggregateTrust(results);
    return new RenderedWidget(w.id(), spec, null, trust);
  }

  /**
   * Collapses per-metric trust into a single worst-case summary for a widget. A composite widget
   * must not report "verified" while any of its metrics is stale or conflicted, so the state is the
   * least-trusted {@link TrustState} (by enum ordinal) and the freshness is the worst {@link
   * FreshnessState}; the provenance fields are copied from the first result attaining the worst
   * state. A single metric keeps its trust unchanged.
   */
  private static TrustSummary aggregateTrust(List<MetricResult> results) {
    if (results.size() == 1) {
      return results.get(0).provenance().trust();
    }
    MetricResult worst = null;
    TrustState worstState = null;
    FreshnessState worstFreshness = null;
    for (MetricResult result : results) {
      TrustSummary trust = result.provenance().trust();
      if (trust == null) continue;
      if (worstState == null || trust.state().ordinal() > worstState.ordinal()) {
        worstState = trust.state();
        worst = result;
      }
      if (worstFreshness == null
          || freshnessRank(trust.freshness()) > freshnessRank(worstFreshness)) {
        worstFreshness = trust.freshness();
      }
    }
    if (worst == null) {
      return null;
    }
    TrustSummary base = worst.provenance().trust();
    return new TrustSummary(
        worstState,
        worstFreshness,
        base.authoritativeSource(),
        base.resolvedAt(),
        base.lastIngestionAt(),
        base.threshold());
  }

  /** Worst-first freshness ranking: SOURCE_FAILURE &gt; STALE &gt; UNKNOWN &gt; FRESH. */
  private static int freshnessRank(FreshnessState freshness) {
    return switch (freshness) {
      case FRESH -> 0;
      case UNKNOWN -> 1;
      case STALE -> 2;
      case SOURCE_FAILURE -> 3;
    };
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
      widgetValidator.validate(w);
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
    List<ShareSnapshot> shareSnapshot =
        shares.findByDashboardId(saved.id()).stream()
            .map(s -> new ShareSnapshot(s.department(), s.seniority()))
            .toList();
    revisionsRepository.save(
        SavedDashboardRevision.create(
            saved.id(),
            saved.currentRevision(),
            serialize(new RevisionSnapshot(toDocument(saved), shareSnapshot)),
            actorEmail,
            CLOCK.instant()));
  }

  private SavedDashboardRevision findRevision(UUID id, int revision) {
    return revisionsRepository.findByDashboardIdOrderByRevisionDesc(id).stream()
        .filter(r -> r.revision() == revision)
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("No revision " + revision + " for dashboard " + id));
  }

  private String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize dashboard revision", e);
    }
  }

  private RevisionSnapshot readSnapshot(String json) {
    try {
      return mapper.readValue(json, RevisionSnapshot.class);
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

  public record DashboardSummary(
      UUID id,
      String title,
      String description,
      String createdBy,
      boolean pinned,
      String visibility,
      String updatedAt) {}

  public record DashboardRevisionSummary(int revision, String createdBy, Instant createdAt) {}

  /** Visibility plus the role list that can open a {@code SHARED} dashboard. */
  public record DashboardSharing(Visibility visibility, List<UserRole> roles) {}

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
  public record RenderedWidget(
      String widgetId, WidgetSpec widget, String deniedResource, TrustSummary trust) {}

  /**
   * A revision snapshot: the document plus the share roles in force when the revision was written.
   */
  record RevisionSnapshot(DashboardDocument document, List<ShareSnapshot> shares) {}

  /** One share role captured in a revision snapshot (department × seniority). */
  record ShareSnapshot(String department, String seniority) {}
}
