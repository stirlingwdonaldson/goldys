package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DailySalesTriggerIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired DailySalesOverrideService overrideService;
  @Autowired ResolutionRuleService ruleService;
  @Autowired ResolvedDailySalesRepository resolved;
  // The real PermissionService would consult the seeded permission table; replace it so the
  // override/rule saves succeed without a permission fixture. A Mockito mock's void require(...)
  // is a no-op by default.
  @MockitoBean PermissionService permissions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
    jdbc.update("truncate table daily_sales_override");
    jdbc.update("truncate table resolution_rule");
  }

  @Test
  void recordingCanonicalProjectsTheDateAutomatically() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));

    // No explicit projector call — the event listener must have projected the conflict.
    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isTrue();
    assertThat(row.resolutionType()).isEqualTo("conflict");
  }

  @Test
  void overrideSaveProjectsTheDate() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));

    overrideService.save(OWNER, "owner@example.com", SEP_13, "LIGHTSPEED", "trust lightspeed");

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isFalse();
    assertThat(row.resolutionType()).isEqualTo("override");
    assertThat(row.authoritativeSource()).isEqualTo("LIGHTSPEED");
  }

  @Test
  void dailySalesRuleChangeRecomputesAllDates() {
    LocalDate sep14 = LocalDate.of(2026, 9, 14);
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    ingest.record(input("LIGHTSPEED", sep14, "1000.00"));
    ingest.record(input("CTB", sep14, "2000.00"));

    ruleService.save(
        OWNER,
        "owner@example.com",
        new ResolutionRuleService.RuleInput(
            "daily_sales", "daily_sales", "priority", null, List.of("CTB")));

    assertThat(resolved.findTopByOrderByTradingDateDesc().get().hasConflict()).isFalse();
    for (ResolvedDailySales row : resolved.findAll()) {
      assertThat(row.hasConflict()).isFalse();
      assertThat(row.authoritativeSource()).isEqualTo("CTB");
    }
  }

  private DailySalesInput input(String source, LocalDate date, String total) {
    return new DailySalesInput(
        source,
        date,
        new BigDecimal(total),
        new BigDecimal("0"),
        new BigDecimal("0"),
        rawRecord(source));
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id) — insert real rows.
  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
