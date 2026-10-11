package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalInventoryQuery;
import com.goldys.platform.canonical.CanonicalInvoiceIngest;
import com.goldys.platform.canonical.CanonicalInvoiceLineIngest;
import com.goldys.platform.canonical.InvoiceInput;
import com.goldys.platform.canonical.InvoiceLineInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Convergence: when the CTB CSV later includes the lines it once omitted, the PDF-derived lines the
 * gap-recovery path recorded for that invoice are bitemporally closed (superseded, never deleted)
 * so they stop contributing and only the CSV line remains current.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InvoiceGapConvergenceIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceIngest invoiceIngest;
  @Autowired CanonicalInvoiceLineIngest lineIngest;
  @Autowired CanonicalInventoryQuery inventoryQuery;
  @Autowired CtInvoiceCsvIngestService csvIngest;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
    jdbc.update("truncate table canonical_invoice");
    jdbc.update("truncate table invoice_ingest_flag");
  }

  @Test
  void supersedesPdfOriginLinesLeavingOnlyTheCsvLineCurrent() {
    LocalDate invoiceDate = LocalDate.of(2026, 10, 1);
    lineIngest.record(line("INV-7300", invoiceDate, "60.00", "PDF", "INV-7300:pdf:1"));
    lineIngest.record(line("INV-7300", invoiceDate, "60.00", "CSV", "INV-7300:1"));

    int superseded = lineIngest.supersedePdfOriginLines("INV-7300");

    assertThat(superseded).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line "
                    + "where invoice_number='INV-7300' and origin='PDF' and superseded_at is not null",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line "
                    + "where invoice_number='INV-7300' and superseded_at is null",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select origin from canonical_invoice_line "
                    + "where invoice_number='INV-7300' and superseded_at is null",
                String.class))
        .isEqualTo("CSV");
  }

  @Test
  void returnsZeroForAnInvoiceWithNoPdfOriginLines() {
    lineIngest.record(line("INV-7301", LocalDate.of(2026, 10, 1), "60.00", "CSV", "INV-7301:1"));

    assertThat(lineIngest.supersedePdfOriginLines("INV-7301")).isZero();
  }

  @Test
  void csvIngestSupersedesPdfLinesWhenTheCsvBecomesComplete() {
    LocalDate invoiceDate = LocalDate.of(2026, 10, 1);
    // Pre-convergence state (after gap recovery): header 100, one CSV line 40, one PDF gap line 60.
    invoiceIngest.record(
        new InvoiceInput(
            "CTB",
            "INV-7302",
            "Bruno's",
            invoiceDate,
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
    lineIngest.record(line("INV-7302", invoiceDate, "40.00", "CSV", "INV-7302:1"));
    lineIngest.record(line("INV-7302", invoiceDate, "60.00", "PDF", "INV-7302:pdf:1"));

    // The fixed CSV now carries both lines (40 + 60 = 100) and the header total 100.
    byte[] csv =
        ("Invoice,Supplier,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax,InvoiceTotalExTax\n"
                + "INV-7302,Bruno's,2026-10-01,STK-7,Beer,1 EACH,40.00,100.00\n"
                + "INV-7302,Bruno's,2026-10-01,STK-8,Chips,1 EACH,60.00,100.00\n")
            .getBytes(StandardCharsets.UTF_8);

    csvIngest.ingest(csv);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line "
                    + "where invoice_number='INV-7302' and origin='PDF' and superseded_at is not null",
                Integer.class))
        .isEqualTo(1);
    assertThat(inventoryQuery.currentLineTotalForInvoice("INV-7302")).isEqualByComparingTo("100");
  }

  private InvoiceLineInput line(
      String invoiceNumber, LocalDate date, String total, String origin, String ref) {
    return new InvoiceLineInput(
        "CTB",
        ref,
        invoiceNumber,
        date,
        "item",
        null,
        BigDecimal.ONE,
        new BigDecimal(total),
        new BigDecimal(total),
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
