package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
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
import com.goldys.platform.semantic.catalog.MetricPoint;
import com.goldys.platform.semantic.catalog.MetricProvenance;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.MetricQueryService;
import com.goldys.platform.semantic.catalog.MetricSeries;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.semantic.catalog.TimeSeriesResult;
import com.goldys.platform.widget.WidgetSpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardRenderTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final TimeRange RANGE = new TimeRange(SEP_13, SEP_13, Calendar.CALENDAR);

  private final SavedDashboardRepository repo = mock(SavedDashboardRepository.class);
  private final MetricCatalog catalog = new MetricCatalog();
  private final WidgetRenderer renderer = new WidgetRenderer(catalog);
  private final ObjectMapper mapper = new ObjectMapper();
  private final PermissionService permissions = mock(PermissionService.class);
  private final SavedDashboardRevisionRepository revisions =
      mock(SavedDashboardRevisionRepository.class);
  private final SavedDashboardShareRepository shares = mock(SavedDashboardShareRepository.class);
  private final MetricQueryService metricQueryService = mock(MetricQueryService.class);

  private final SavedDashboardApplicationService service =
      new SavedDashboardApplicationService(
          repo, catalog, renderer, mapper, permissions, revisions, shares, metricQueryService);

  @Test
  void deniedMetricRendersExplicitDenialNotPartial() {
    var boh = new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
    SavedDashboard saved =
        SavedDashboard.create(
            "Labour",
            null,
            "grid",
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(query(MetricId.LABOUR_COST)),
                    new WidgetLayout(6, 2))),
            DashboardFilters.empty(),
            Visibility.PRIVATE,
            "boh@x.com",
            Instant.EPOCH);
    when(repo.findById(saved.id())).thenReturn(Optional.of(saved));
    doThrow(new AccessDeniedException("Access denied for resource: labour.cost"))
        .when(permissions)
        .require(eq(boh), eq(new ResourceKey("labour.cost")), eq(PermissionAction.READ));

    List<SavedDashboardApplicationService.RenderedWidget> r =
        service.render(boh, "boh@x.com", saved.id());

    assertThat(r).hasSize(1);
    assertThat(r.get(0).widget()).isNull();
    assertThat(r.get(0).deniedResource()).isEqualTo("labour.cost");
  }

  @Test
  void allowedMetricRendersAWidgetSpec() {
    var owner = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
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
            "owner@x.com",
            Instant.EPOCH);
    when(repo.findById(saved.id())).thenReturn(Optional.of(saved));
    when(metricQueryService.query(query(MetricId.SALES_GROSS)))
        .thenReturn(
            new TimeSeriesResult(
                MetricId.SALES_GROSS,
                List.of(
                    new MetricSeries(
                        null, List.of(new MetricPoint(SEP_13, new BigDecimal("100"))))),
                List.of(),
                new MetricProvenance(
                    MetricId.SALES_GROSS,
                    "1",
                    RANGE,
                    TimeGrain.DAY,
                    "resolved_daily_sales",
                    Instant.EPOCH,
                    List.of(),
                    "1")));

    List<SavedDashboardApplicationService.RenderedWidget> r =
        service.render(owner, "owner@x.com", saved.id());

    assertThat(r).hasSize(1);
    assertThat(r.get(0).widget()).isInstanceOf(WidgetSpec.class);
    assertThat(r.get(0).deniedResource()).isNull();
  }

  private static MetricQuery query(MetricId id) {
    return new MetricQuery(id, RANGE, TimeGrain.DAY, Set.of(), null);
  }
}
