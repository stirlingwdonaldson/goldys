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
class CanonicalInvoiceLineEnrichmentIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceLineService lineService;
  @Autowired CanonicalInvoiceLineEnrichment enrichment;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line");
  }

  @Test
  void enrichesUomWithoutClobberingCsvTotals() {
    lineService.record(
        new InvoiceLineInput(
            "CTB",
            "INV-9001:1",
            "INV-9001",
            LocalDate.of(2026, 9, 20),
            "beef rump cap",
            "BEEF025",
            new BigDecimal("3.25"),
            new BigDecimal("31.50"),
            new BigDecimal("102.38"),
            null,
            null,
            null,
            null,
            null,
            rawRecord()));

    enrichment.enrich("INV-9001", "BEEF025", "KG", null, null, null);

    assertThat(
            jdbc.queryForObject(
                "select quantity || '|' || line_total || '|' || uom from canonical_invoice_line "
                    + "where invoice_number = 'INV-9001' and superseded_at is null",
                String.class))
        .isEqualTo("3.2500|102.3800|KG");
  }

  @Test
  void ignoresPdfLinesWithNoCsvMatch() {
    lineService.record(
        new InvoiceLineInput(
            "CTB",
            "INV-9002:1",
            "INV-9002",
            LocalDate.of(2026, 9, 20),
            "beef",
            "BEEF025",
            new BigDecimal("3.25"),
            new BigDecimal("31.50"),
            new BigDecimal("102.38"),
            null,
            null,
            null,
            null,
            null,
            rawRecord()));

    enrichment.enrich("INV-9002", "NOPE", "KG", null, null, null);

    assertThat(
            jdbc.queryForObject(
                "select uom from canonical_invoice_line where invoice_number = 'INV-9002'",
                String.class))
        .isNull();
  }

  @Test
  void reEnrichingWithSameFieldsDoesNotAppendAVersion() {
    lineService.record(
        new InvoiceLineInput(
            "CTB",
            "INV-9003:1",
            "INV-9003",
            LocalDate.of(2026, 9, 20),
            "beef",
            "BEEF025",
            new BigDecimal("3.25"),
            new BigDecimal("31.50"),
            new BigDecimal("102.38"),
            null,
            null,
            null,
            null,
            null,
            rawRecord()));

    enrichment.enrich("INV-9003", "BEEF025", "KG", null, null, null);
    enrichment.enrich("INV-9003", "BEEF025", "KG", null, null, null);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where invoice_number = 'INV-9003'",
                Integer.class))
        .isEqualTo(2); // original superseded + one current (no third row)
  }

  @Test
  void csvReingestAfterEnrichmentPreservesEnrichment() {
    InvoiceLineInput csv =
        new InvoiceLineInput(
            "CTB",
            "INV-9004:1",
            "INV-9004",
            LocalDate.of(2026, 9, 20),
            "beef rump cap",
            "BEEF025",
            new BigDecimal("3.25"),
            new BigDecimal("31.50"),
            new BigDecimal("102.38"),
            null,
            null,
            null,
            null,
            null,
            rawRecord());
    lineService.record(csv);

    enrichment.enrich("INV-9004", "BEEF025", "KG", null, null, null);

    // Re-ingest the same CSV line (uom=null) — enrichment must survive, no version churn.
    lineService.record(csv);

    assertThat(
            jdbc.queryForObject(
                "select uom from canonical_invoice_line where invoice_number = 'INV-9004' and superseded_at is null",
                String.class))
        .isEqualTo("KG");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where invoice_number = 'INV-9004'",
                Integer.class))
        .isEqualTo(2);
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
