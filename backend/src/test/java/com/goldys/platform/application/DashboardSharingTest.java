package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.DashboardTemplateCatalog;
import com.goldys.platform.dashboard.DashboardWidgetValidator;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedDashboardRevisionRepository;
import com.goldys.platform.dashboard.SavedDashboardShare;
import com.goldys.platform.dashboard.SavedDashboardShareRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.reporting.WidgetRenderer;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardSharingTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);

  private final SavedDashboardRepository repo = mock(SavedDashboardRepository.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final WidgetRenderer renderer = new WidgetRenderer(catalog);
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final PermissionService permissions = mock(PermissionService.class);
  private final SavedDashboardRevisionRepository revisions =
      mock(SavedDashboardRevisionRepository.class);
  private final SavedDashboardShareRepository shares = mock(SavedDashboardShareRepository.class);
  private final MetricQueryService metricQueryService = mock(MetricQueryService.class);

  private final SavedDashboardApplicationService service =
      new SavedDashboardApplicationService(
          repo,
          catalog,
          new DashboardWidgetValidator(catalog),
          renderer,
          mapper,
          permissions,
          revisions,
          shares,
          metricQueryService,
          new DashboardTemplateCatalog());

  private final AtomicReference<SavedDashboard> lastSaved = new AtomicReference<>();

  @BeforeEach
  void stubRepositorySave() {
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              SavedDashboard s = inv.getArgument(0);
              lastSaved.set(s);
              return s;
            });
    when(shares.findByDashboardId(any())).thenReturn(List.of());
  }

  @Test
  void privateDashboardIsCreatorOnly() {
    var d = service.create(OWNER, "a@b.com", input());
    when(repo.findById(d.id())).thenReturn(Optional.of(lastSaved.get()));

    var other = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

    assertThatThrownBy(() -> service.get(other, "c@d.com", d.id()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void sharingAndRevisionsRequireVisibility() {
    var d = service.create(OWNER, "a@b.com", input());
    when(repo.findById(d.id())).thenReturn(Optional.of(lastSaved.get()));

    var other = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

    assertThatThrownBy(() -> service.sharing(other, "c@d.com", d.id()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.revisions(other, "c@d.com", d.id()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void sharedRoleCanViewDashboard() {
    var d = service.create(OWNER, "a@b.com", input());
    when(repo.findById(d.id())).thenReturn(Optional.of(lastSaved.get()));

    var boh = new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
    when(shares.findByDashboardId(d.id()))
        .thenReturn(List.of(SavedDashboardShare.create(d.id(), "BOH", "MANAGER")));

    service.setSharing(OWNER, "a@b.com", d.id(), Visibility.SHARED, List.of(boh));

    var doc = service.get(boh, "boh@x.com", d.id());
    assertThat(doc).isNotNull();
    assertThat(doc.visibility()).isEqualTo(Visibility.SHARED);
    assertThat(service.sharing(OWNER, "a@b.com", d.id()).roles()).containsExactly(boh);
  }

  private static SavedDashboardApplicationService.DashboardInput input() {
    return new SavedDashboardApplicationService.DashboardInput(
        "Sales",
        null,
        "grid",
        DashboardFilters.empty(),
        Visibility.PRIVATE,
        List.of(
            new SavedWidget(
                "w1",
                "time-series",
                List.of(
                    new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null)),
                new WidgetLayout(6, 2))));
  }
}
