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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gap recovery is idempotent. On the first call the header (100) reconciles with the PDF sum but
 * the CSV-only line sum (40) is short, so one {@code origin='PDF'} gap line is recorded. On the
 * second call, {@code recover} recomputes the CSV-only line sum — which deliberately excludes the
 * PDF gap line it recorded, so it is still 40 — and re-classifies the invoice as
 * PDF-complete/CSV-short. It proceeds to record the same gap-line identity; the canonical layer's
 * {@code sameFact} sees an identical fact (same {@code source_record_ref}) and returns without
 * creating a new version. No second line is created, so the invoice's current line total is
 * unchanged.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InvoiceGapRecoveryIdempotenceIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceIngest invoiceIngest;
  @Autowired CanonicalInvoiceLineIngest lineIngest;
  @Autowired CanonicalInventoryQuery inventoryQuery;
  @Autowired InvoiceGapRecoveryService recovery;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
    jdbc.update("truncate table canonical_invoice");
    jdbc.update("truncate table invoice_ingest_flag");
  }

  @Test
  void reRunningRecoveryOverTheSamePdfIsANoOp() {
    LocalDate invoiceDate = LocalDate.of(2026, 10, 1);
    byte[] pdf = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
    UUID pdfRawRecordId = rawRecord();

    // Header 100, CSV line 40 — the PDF's 60.00 line is the missing 60 of the shortfall.
    invoiceIngest.record(
        new InvoiceInput(
            "CTB",
            "INV-7401",
            "Test Supplier",
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
            "a.pdf",
            pdfRawRecordId));
    lineIngest.record(
        new InvoiceLineInput(
            "CTB",
            "INV-7401:1",
            "INV-7401",
            invoiceDate,
            "csv item",
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

    PdfExtractedLine omittedLine =
        new PdfExtractedLine(
            "CODE",
            "pdf item",
            new BigDecimal("3"),
            "KG",
            null,
            null,
            null,
            new BigDecimal("60.00"));
    PdfExtractedLine alreadyInCsvLine =
        new PdfExtractedLine(
            "CODE2", "csv item", BigDecimal.ONE, "KG", null, null, null, new BigDecimal("40.00"));

    String gapRef = "INV-7401:pdf:" + sha256(pdf) + ":0";

    recovery.recover(
        "INV-7401",
        "a.pdf",
        pdf,
        pdfRawRecordId,
        List.of(omittedLine, alreadyInCsvLine),
        Set.of(0));
    recovery.recover(
        "INV-7401",
        "a.pdf",
        pdf,
        pdfRawRecordId,
        List.of(omittedLine, alreadyInCsvLine),
        Set.of(0));

    // Exactly one current PDF-origin line for that identity, and no historical version: the second
    // recover re-records the identical fact (same source_record_ref), which the canonical layer's
    // sameFact treats as a no-op rather than creating a second version.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line "
                    + "where source_record_ref = ? and superseded_at is null",
                Integer.class,
                gapRef))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where source_record_ref = ?",
                Integer.class,
                gapRef))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line "
                    + "where invoice_number='INV-7401' and origin='PDF'",
                Integer.class))
        .isEqualTo(1);
    // The invoice's current line total is unchanged (CSV 40 + PDF gap 60 = 100).
    assertThat(inventoryQuery.currentLineTotalForInvoice("INV-7401")).isEqualByComparingTo("100");
    // Recovery compares the CSV-only line total, not the all-lines total: had it summed all lines,
    // the first gap line would have made the second call look CSV-complete and skip recording
    // entirely. The CSV-only total staying at 40 confirms the intended mechanism (sameFact no-op).
    assertThat(inventoryQuery.currentCsvLineTotalForInvoice("INV-7401")).isEqualByComparingTo("40");
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

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
