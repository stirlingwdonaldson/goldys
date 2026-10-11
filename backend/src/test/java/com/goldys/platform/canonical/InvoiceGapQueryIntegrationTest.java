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
class InvoiceGapQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceIngest invoiceIngest;
  @Autowired CanonicalInvoiceLineIngest lineIngest;
  @Autowired CanonicalInvoiceQuery invoiceQuery;
  @Autowired CanonicalInventoryQuery inventoryQuery;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
    jdbc.update("truncate table canonical_invoice");
  }

  @Test
  void returnsHeaderExTaxAndCsvLineSumPerInvoice() {
    invoiceIngest.record(
        new InvoiceInput(
            "CTB",
            "INV-7001",
            "Test Supplier",
            LocalDate.of(2026, 10, 1),
            null,
            new BigDecimal("100.00"),
            null,
            null,
            null,
            new BigDecimal("100.00"),
            null,
            null,
            null,
            null,
            rawRecord()));
    lineIngest.record(
        new InvoiceLineInput(
            "CTB",
            "INV-7001:1",
            "INV-7001",
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
            "CSV"));
    lineIngest.record(
        new InvoiceLineInput(
            "CTB",
            "INV-7001:2",
            "INV-7001",
            LocalDate.of(2026, 10, 1),
            "item b",
            null,
            new BigDecimal("1"),
            new BigDecimal("40.00"),
            new BigDecimal("40.00"),
            null,
            null,
            null,
            null,
            null,
            rawRecord(),
            "CSV"));

    assertThat(invoiceQuery.amountExTaxForInvoiceNumber("INV-7001"))
        .contains(new BigDecimal("100.0000"));
    assertThat(inventoryQuery.currentLineTotalForInvoice("INV-7001"))
        .isEqualByComparingTo("70.0000");
  }

  @Test
  void lineSumIsZeroForAnInvoiceWithNoLines() {
    assertThat(inventoryQuery.currentLineTotalForInvoice("INV-9999")).isEqualByComparingTo("0");
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
