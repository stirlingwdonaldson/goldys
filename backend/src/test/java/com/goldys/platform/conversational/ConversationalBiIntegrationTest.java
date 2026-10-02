package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.reporting.ReportingTool;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolRegistry;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ConversationalBiIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest dailySales;
  @Autowired ToolDispatcher dispatcher;
  @Autowired ToolRegistry registry;
  @Autowired ReportingToolCallbacks callbacks;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_daily_sales, daily_sales_override, resolution_rule");
  }

  @Test
  void runsGetSalesByPeriodThroughTheCallbackToTheResolvedView() throws Exception {
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "CTB", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));

    ReportingTool tool =
        registry.find(com.goldys.platform.reporting.ToolId.GET_SALES_BY_PERIOD).orElseThrow();
    ConversationContext context = new ConversationContext();
    ToolCallback callback =
        callbacks.forTools(List.of(tool), OWNER, context, dispatcher, mapper).get(0);

    String raw =
        callback.call(
            "{\"startDate\":\"2026-09-13\",\"endDate\":\"2026-09-13\",\"metric\":\"GROSS_SALES\"}");

    assertThat(raw).contains("27650.66");
    assertThat(context.toAnswerPayload().widgets()).hasSize(1);
    assertThat(context.toAnswerPayload().widgets().get(0).type()).isEqualTo("line-chart");
    assertThat(context.toAnswerPayload().notices()).isEmpty();
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
