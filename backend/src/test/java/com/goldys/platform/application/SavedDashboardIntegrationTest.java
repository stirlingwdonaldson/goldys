package com.goldys.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SavedDashboardIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest dailySales;
  @Autowired SavedDashboardApplicationService service;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table saved_dashboard, canonical_daily_sales, daily_sales_override, resolution_rule");
  }

  @Test
  void saveThenRenderReRunsTheStoredQueryAgainstCurrentData() {
    dailySales.record(
        new DailySalesInput("LIGHTSPEED", SEP_13, bd("27650.66"), bd("0"), bd("0"), rawRecord()));
    dailySales.record(
        new DailySalesInput("CTB", SEP_13, bd("27650.66"), bd("0"), bd("0"), rawRecord()));

    var input =
        new SavedDashboardApplicationService.DashboardInput(
            "Weekly sales",
            null,
            "grid",
            List.of(
                new SavedWidget(
                    "w1",
                    "GET_SALES_BY_PERIOD",
                    Map.of(
                        "startDate",
                        "2026-09-13",
                        "endDate",
                        "2026-09-13",
                        "metric",
                        "GROSS_SALES"))));

    var created = service.create(OWNER, "a@b.com", input);
    assertThat(created.id()).isNotNull();
    assertThat(created.widgets()).hasSize(1);

    var specs = service.render(OWNER, created.id());
    assertThat(specs).hasSize(1);
    assertThat(specs.get(0)).isInstanceOf(TimeSeriesWidgetSpec.class);
    var widget = (TimeSeriesWidgetSpec) specs.get(0);
    assertThat(widget.series().get(0).points().get(0).y()).isEqualByComparingTo("27650.66");
  }

  @Test
  void rejectsAnUnsupportedLayout() {
    assertThatThrownBy(
            () ->
                service.create(
                    OWNER,
                    "a@b.com",
                    new SavedDashboardApplicationService.DashboardInput(
                        "X", null, "masonry", List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("layout");
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
