package com.goldys.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import com.goldys.platform.widget.TimeSeriesWidgetSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ReportingToolIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest dailySales;
  @Autowired ToolDispatcher dispatcher;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_daily_sales, daily_sales_override, resolution_rule");
  }

  @Test
  void returnsTheResolvedTotalEndToEnd() {
    // Two sources agree on 13 Sep, so the resolved total is that value.
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "CTB", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));

    ToolResult result =
        dispatcher.dispatch(
            ToolId.GET_SALES_BY_PERIOD,
            new GetSalesByPeriodInput(SEP_13, SEP_13, Metric.GROSS_SALES),
            OWNER);

    assertThat(result.widget()).isInstanceOf(TimeSeriesWidgetSpec.class);
    var widget = (TimeSeriesWidgetSpec) result.widget();
    assertThat(widget.series()).hasSize(1);
    assertThat(widget.series().get(0).points().get(0).y()).isEqualByComparingTo("27650.66");
    assertThat(result.notices()).isEmpty();
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
