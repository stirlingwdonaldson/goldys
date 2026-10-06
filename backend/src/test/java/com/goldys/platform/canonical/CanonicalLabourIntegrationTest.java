package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
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
class CanonicalLabourIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalLabourEntryService service;
  @Autowired CanonicalLabourEntryRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_labour_entry");
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    LabourInput input = input("shift-1", "FOH", "8.00", "7.50");

    CanonicalLabourEntry first = service.record(input);
    CanonicalLabourEntry retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
  }

  @Test
  void changedFactSupersedesThePriorVersion() {
    CanonicalLabourEntry first = service.record(input("shift-1", "FOH", "8.00", "7.50"));
    CanonicalLabourEntry corrected = service.record(input("shift-1", "FOH", "8.00", "8.25"));

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findAllCurrent()).hasSize(1);
    assertThat(repository.findAllCurrent().get(0).actualHours())
        .isEqualByComparingTo(new BigDecimal("8.25"));
  }

  private LabourInput input(
      String ref, String department, String scheduledHours, String actualHours) {
    return new LabourInput(
        "DEPUTY",
        ref,
        "staff-1",
        department,
        LocalDate.of(2026, 9, 20),
        new BigDecimal(scheduledHours),
        new BigDecimal(actualHours),
        null,
        new BigDecimal("250.00"),
        Instant.parse("2026-09-20T02:00:00Z"),
        Instant.parse("2026-09-20T08:00:00Z"),
        rawRecord());
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
