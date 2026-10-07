package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedDashboardRevisionRepository;
import com.goldys.platform.dashboard.SavedDashboardShareRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.reporting.WidgetRenderer;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SavedDashboardApplicationServiceTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final TimeRange RANGE = new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR);

  private final SavedDashboardRepository repo = mock(SavedDashboardRepository.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final WidgetRenderer renderer = new WidgetRenderer(catalog);
  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
  private final com.goldys.platform.auth.PermissionService permissions =
      mock(com.goldys.platform.auth.PermissionService.class);
  private final SavedDashboardRevisionRepository revisions =
      mock(SavedDashboardRevisionRepository.class);
  private final SavedDashboardShareRepository shares = mock(SavedDashboardShareRepository.class);
  private final com.goldys.platform.semantic.catalog.MetricQueryService metricQueryService =
      mock(com.goldys.platform.semantic.catalog.MetricQueryService.class);

  private final SavedDashboardApplicationService service =
      new SavedDashboardApplicationService(
          repo, catalog, renderer, mapper, permissions, revisions, shares, metricQueryService);

  @Test
  void createRejectsUnknownMetric() {
    MetricCatalog strict = mock(MetricCatalog.class);
    when(strict.definition(MetricId.SALES_GROSS))
        .thenThrow(new IllegalArgumentException("Unknown metric: sales.gross"));
    var strictService =
        new SavedDashboardApplicationService(
            repo,
            strict,
            new WidgetRenderer(strict),
            mapper,
            permissions,
            revisions,
            shares,
            metricQueryService);

    var input =
        new SavedDashboardApplicationService.DashboardInput(
            "X",
            null,
            "grid",
            DashboardFilters.empty(),
            Visibility.PRIVATE,
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(
                        new MetricQuery(
                            MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null)),
                    new WidgetLayout(6, 2))));

    assertThatThrownBy(() -> strictService.create(OWNER, "a@b.com", input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown metric");
  }

  @Test
  void createRejectsBlankTitle() {
    assertThatThrownBy(
            () ->
                service.create(OWNER, "a@b.com", input("  ", "time-series", MetricId.SALES_GROSS)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void createRejectsUnsupportedLayout() {
    assertThatThrownBy(
            () ->
                service.create(
                    OWNER,
                    "a@b.com",
                    new SavedDashboardApplicationService.DashboardInput(
                        "X",
                        null,
                        "masonry",
                        DashboardFilters.empty(),
                        Visibility.PRIVATE,
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("layout");
  }

  @Test
  void createRejectsUnknownRenderType() {
    assertThatThrownBy(
            () -> service.create(OWNER, "a@b.com", input("X", "pie-chart", MetricId.SALES_GROSS)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("render type");
  }

  @Test
  void createRejectsTooManyQueries() {
    var widget =
        new SavedWidget(
            "w1",
            "table",
            List.of(
                query(MetricId.SALES_GROSS),
                query(MetricId.SALES_NET),
                query(MetricId.SALES_GST),
                query(MetricId.LABOUR_COST),
                query(MetricId.LABOUR_HOURS_VARIANCE)),
            new WidgetLayout(12, 2));
    var input =
        new SavedDashboardApplicationService.DashboardInput(
            "X", null, "grid", DashboardFilters.empty(), Visibility.PRIVATE, List.of(widget));

    assertThatThrownBy(() -> service.create(OWNER, "a@b.com", input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 4");
  }

  @Test
  void createRejectsNoQueries() {
    var widget = new SavedWidget("w1", "table", List.of(), new WidgetLayout(12, 2));
    var input =
        new SavedDashboardApplicationService.DashboardInput(
            "X", null, "grid", DashboardFilters.empty(), Visibility.PRIVATE, List.of(widget));

    assertThatThrownBy(() -> service.create(OWNER, "a@b.com", input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 4");
  }

  @Test
  void createPersistsAndReturnsVersionTwoDocument() {
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var doc = service.create(OWNER, "a@b.com", input("Sales", "time-series", MetricId.SALES_GROSS));

    assertThat(doc.schemaVersion()).isEqualTo(2);
    assertThat(doc.title()).isEqualTo("Sales");
    assertThat(doc.layout()).isEqualTo("grid");
    assertThat(doc.visibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(doc.pinned()).isFalse();
    assertThat(doc.widgets()).hasSize(1);
    verify(repo).save(any(SavedDashboard.class));
  }

  @Test
  void getReturnsTheStoredDocument() {
    SavedDashboard saved =
        SavedDashboard.create(
            "Sales",
            null,
            "grid",
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(query(MetricId.SALES_GROSS)),
                    new WidgetLayout(6, 2))),
            DashboardFilters.empty(),
            Visibility.PRIVATE,
            "a@b.com",
            java.time.Instant.EPOCH);
    when(repo.findById(saved.id())).thenReturn(java.util.Optional.of(saved));

    var doc = service.get(OWNER, "a@b.com", saved.id());

    assertThat(doc.title()).isEqualTo("Sales");
    assertThat(doc.schemaVersion()).isEqualTo(2);
  }

  private static MetricQuery query(MetricId id) {
    return new MetricQuery(id, RANGE, TimeGrain.DAY, Set.of(), null);
  }

  private static SavedDashboardApplicationService.DashboardInput input(
      String title, String renderType, MetricId id) {
    return new SavedDashboardApplicationService.DashboardInput(
        title,
        null,
        "grid",
        DashboardFilters.empty(),
        Visibility.PRIVATE,
        List.of(new SavedWidget("w1", renderType, List.of(query(id)), new WidgetLayout(6, 2))));
  }
}
