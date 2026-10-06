package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalLabourIngest;
import com.goldys.platform.canonical.LabourInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LabourProjectorIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalLabourIngest ingest;
  @Autowired LabourProjector projector;
  @Autowired ResolvedLabourDayRepository resolved;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_labour_entry");
    jdbc.update("truncate table resolved_labour_day");
    jdbc.update("truncate table labour_override");
  }

  @Test
  void sumsHoursAndCostPerDepartment() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    recordLabour("shift-1", "FOH", "8.00", "7.50", "200.00");
    recordLabour("shift-2", "FOH", "6.00", "6.00", "150.00");
    recordLabour("shift-3", "BOH", "7.00", "7.00", "180.00");

    projector.recompute(d);

    ResolvedLabourDay foh = row(d, "FOH");
    assertThat(foh.scheduledHours()).isEqualByComparingTo(new BigDecimal("14.00"));
    assertThat(foh.actualHours()).isEqualByComparingTo(new BigDecimal("13.50"));
    assertThat(foh.actualCost()).isEqualByComparingTo(new BigDecimal("350.00"));

    ResolvedLabourDay boh = row(d, "BOH");
    assertThat(boh.scheduledHours()).isEqualByComparingTo(new BigDecimal("7.00"));
  }

  @Test
  void nullCostPropagatesToNullDayCost() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    recordLabour("shift-1", "FOH", "8.00", "7.50", "200.00");
    recordLabour("shift-2", "FOH", "6.00", "6.00", null);

    projector.recompute(d);

    ResolvedLabourDay foh = row(d, "FOH");
    assertThat(foh.actualHours()).isEqualByComparingTo(new BigDecimal("13.50"));
    assertThat(foh.actualCost()).isNull();
  }

  private ResolvedLabourDay row(LocalDate date, String department) {
    List<ResolvedLabourDay> rows =
        resolved.findByTradingDateBetweenOrderByTradingDateAscDepartmentAsc(date, date);
    return rows.stream().filter(r -> r.department().equals(department)).findFirst().orElseThrow();
  }

  private void recordLabour(
      String ref, String department, String scheduled, String actual, String cost) {
    ingest.record(
        new LabourInput(
            "DEPUTY",
            ref,
            "staff-1",
            department,
            LocalDate.of(2026, 9, 20),
            new BigDecimal(scheduled),
            new BigDecimal(actual),
            null,
            cost == null ? null : new BigDecimal(cost),
            Instant.parse("2026-09-20T02:00:00Z"),
            Instant.parse("2026-09-20T08:00:00Z"),
            rawRecord()));
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'DEPUTY', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'DEPUTY', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
