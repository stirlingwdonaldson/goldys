package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
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
class CanonicalInvoiceLineOriginIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceLineIngest lineIngest;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
  }

  @Test
  void recordsTheLineOrigin() {
    lineIngest.record(inputWithOrigin("INV-7100:1", "PDF"));

    assertThat(
            jdbc.queryForObject(
                "select origin from canonical_invoice_line where source_record_ref='INV-7100:1'",
                String.class))
        .isEqualTo("PDF");
  }

  private InvoiceLineInput inputWithOrigin(String sourceRecordRef, String origin) {
    return new InvoiceLineInput(
        "CTB",
        sourceRecordRef,
        "INV-7100",
        null,
        LocalDate.of(2026, 10, 1),
        "item a",
        null,
        new BigDecimal("1"),
        new BigDecimal("30.00"),
        new BigDecimal("30.00"),
        null,
        null,
        null,
        null,
        null,
        rawRecord(),
        origin);
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
}
