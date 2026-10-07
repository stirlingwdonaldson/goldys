package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedDashboard;
import com.goldys.platform.dashboard.SavedDashboardRepository;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Comparison;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DashboardVersioningTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final TimeRange QUERY_RANGE =
      new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);
  private static final TimeRange FILTER_RANGE =
      new TimeRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), Calendar.CALENDAR);

  @Autowired SavedDashboardApplicationService service;
  @Autowired SavedDashboardRepository repository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table saved_dashboard_revision, saved_dashboard_share, saved_dashboard");
  }

  @Test
  void restoreWritesANewRevisionAndRoundTripsLocalDate() {
    var input = inputWithTitle("original");

    var d = service.create(OWNER, "a@b.com", input);

    // Prove the JSONB round-trip: re-reading the saved entity returns the same LocalDate-bearing
    // widgets and filters that went in.
    SavedDashboard reread = repository.findById(d.id()).orElseThrow();
    assertThat(reread.widgets()).isEqualTo(input.widgets());
    assertThat(reread.filters()).isEqualTo(input.filters());

    service.update(OWNER, "a@b.com", d.id(), inputWithTitle("v2"));
    service.update(OWNER, "a@b.com", d.id(), inputWithTitle("v3"));

    var doc = service.restore(OWNER, "a@b.com", d.id(), 1);

    assertThat(doc.title()).isEqualTo("original");
    assertThat(doc.widgets()).isEqualTo(input.widgets());
    assertThat(doc.filters()).isEqualTo(input.filters());
    assertThat(service.revisions(OWNER, "a@b.com", d.id())).hasSize(4); // 3 edits + 1 restore
  }

  @Test
  void restoreReproducesShareRoles() {
    var boh = new UserRole(new DepartmentCode("BOH"), new SeniorityCode("MANAGER"));
    var foh = new UserRole(new DepartmentCode("FOH"), new SeniorityCode("STAFF"));

    var d = service.create(OWNER, "a@b.com", inputWithTitle("v1", Visibility.SHARED));
    service.setSharing(OWNER, "a@b.com", d.id(), Visibility.SHARED, List.of(boh));
    // update writes revision 2, snapshotting visibility=SHARED + shares=[boh]
    service.update(OWNER, "a@b.com", d.id(), inputWithTitle("v2", Visibility.SHARED));
    // drift the share list away from what revision 2 captured
    service.setSharing(OWNER, "a@b.com", d.id(), Visibility.SHARED, List.of(foh));

    service.restore(OWNER, "a@b.com", d.id(), 2);

    var sharing = service.sharing(OWNER, "a@b.com", d.id());
    assertThat(sharing.visibility()).isEqualTo(Visibility.SHARED);
    assertThat(sharing.roles()).containsExactly(boh);
  }

  private static SavedDashboardApplicationService.DashboardInput inputWithTitle(String title) {
    return inputWithTitle(title, Visibility.PRIVATE);
  }

  private static SavedDashboardApplicationService.DashboardInput inputWithTitle(
      String title, Visibility visibility) {
    return new SavedDashboardApplicationService.DashboardInput(
        title,
        "daily sales",
        "grid",
        new DashboardFilters(FILTER_RANGE, Comparison.PREVIOUS_WEEK, Set.of()),
        visibility,
        List.of(
            new SavedWidget(
                "w1",
                "time-series",
                List.of(
                    new MetricQuery(
                        MetricId.SALES_GROSS, QUERY_RANGE, TimeGrain.DAY, Set.of(), null)),
                new WidgetLayout(6, 2))));
  }
}
