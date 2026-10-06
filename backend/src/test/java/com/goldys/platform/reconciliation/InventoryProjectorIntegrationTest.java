package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceLineInput;
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
class InventoryProjectorIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceLineIngest ingest;
  @Autowired InventoryProjector projector;
  @Autowired ResolvedInventoryDayRepository resolved;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
    jdbc.update("truncate table resolved_inventory_day");
    jdbc.update("truncate table inventory_override");
  }

  @Test
  void computesPurchasesFromLineTotalsWithoutInvoiceMetadata() {
    LocalDate d = LocalDate.of(2026, 9, 20);
    // Two lines for INV-1001, with no CanonicalInvoice row present (out-of-order ingest).
    recordLine("INV-1001:1", "INV-1001", d, "potatoes", "25.00");
    recordLine("INV-1001:2", "INV-1001", d, "beef", "45.00");

    projector.recompute(d);

    ResolvedInventoryDay day = resolved.findByTradingDateBetweenOrderByTradingDateAsc(d, d).get(0);
    assertThat(day.purchases()).isEqualByComparingTo(new BigDecimal("70.00"));
    assertThat(day.wastage()).isNull();
    assertThat(day.stockOnHand()).isNull();
  }

  private void recordLine(
      String ref, String invoiceNumber, LocalDate date, String product, String lineTotal) {
    ingest.record(
        new InvoiceLineInput(
            "CTB",
            ref,
            invoiceNumber,
            date,
            product,
            new BigDecimal("2"),
            new BigDecimal("12.50"),
            new BigDecimal(lineTotal),
            null,
            rawRecord()));
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
