package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.Visibility;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.query.SavedQueryService;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.SavedQueryDetail;
import com.goldys.platform.semantic.query.SelectItem;
import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.widget.TableWidgetSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** A saved relational query embedded in a dashboard widget renders as a table, re-authorized. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DashboardQueryWidgetRenderTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));

  @Autowired JdbcTemplate jdbc;
  @Autowired SavedQueryService savedQueries;
  @Autowired SavedDashboardApplicationService dashboards;

  @BeforeEach
  void seed() {
    jdbc.update(
        "truncate table saved_dashboard_revision, saved_dashboard_share, saved_dashboard, "
            + "saved_query_revision, saved_query, resolved_daily_sales");
    jdbc.update(
        "insert into resolved_daily_sales (trading_date, total_sales, net_sales, gst, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values ('2026-10-01', 1000, 900, 100, 'rule', 'CTB', false, now())");
  }

  private QueryPlan salesPlan() {
    return new QueryPlan(
        1,
        "resolved_daily_sales",
        List.of(),
        List.of(
            new SelectItem("trading_date", null, false),
            new SelectItem("total_sales", null, false)),
        null,
        List.of(),
        List.of(),
        100,
        0);
  }

  @Test
  void queryWidgetRendersAsTable() {
    SavedQueryDetail saved = savedQueries.create("owner@example.com", "revenue", salesPlan());

    SavedDashboardApplicationService.DashboardDocument doc =
        dashboards.create(
            OWNER,
            "owner@example.com",
            new SavedDashboardApplicationService.DashboardInput(
                "dash",
                null,
                "grid",
                new DashboardFilters(null, null, Set.of()),
                Visibility.PRIVATE,
                List.of(
                    new SavedWidget(
                        "w1", "query", List.of(), new WidgetLayout(12, 2), saved.id()))));

    List<SavedDashboardApplicationService.RenderedWidget> rendered =
        dashboards.render(OWNER, "owner@example.com", doc.id());

    assertThat(rendered).hasSize(1);
    assertThat(rendered.get(0).widget()).isInstanceOf(TableWidgetSpec.class);
    TableWidgetSpec table = (TableWidgetSpec) rendered.get(0).widget();
    assertThat(table.columns()).extracting(c -> c.key()).contains("trading_date", "total_sales");
    assertThat(table.rows()).hasSize(1);
  }

  @Test
  void queryWidgetReauthorizesForTheViewer() {
    // A saved query over invoice data (inventory.cost) embedded in a dashboard; a STAFF viewer
    // (no inventory.cost) must see a denial, never the data.
    SavedQueryDetail saved =
        savedQueries.create(
            "owner@example.com",
            "invoices",
            new QueryPlan(
                1,
                "invoice",
                List.of(),
                List.of(new SelectItem("invoice_number", null, false)),
                null,
                List.of(),
                List.of(),
                100,
                0));

    SavedDashboardApplicationService.DashboardDocument doc =
        dashboards.create(
            OWNER,
            "owner@example.com",
            new SavedDashboardApplicationService.DashboardInput(
                "dash",
                null,
                "grid",
                new DashboardFilters(null, null, Set.of()),
                Visibility.ORG_WIDE,
                List.of(
                    new SavedWidget(
                        "w1", "query", List.of(), new WidgetLayout(12, 2), saved.id()))));

    List<SavedDashboardApplicationService.RenderedWidget> rendered =
        dashboards.render(STAFF, "owner@example.com", doc.id());

    assertThat(rendered).hasSize(1);
    assertThat(rendered.get(0).widget()).isNull();
    assertThat(rendered.get(0).deniedResource()).isEqualTo("query");
  }
}
