package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
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
class CanonicalBrowseQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalBrowseQuery browse;
  @Autowired CanonicalDailySalesService dailySalesService;
  @Autowired CanonicalInvoiceIngest invoiceIngest;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table raw_record, ingestion_failure, ingestion_run cascade");
  }

  @Test
  void entitiesListsTwelveWithShiftAsPlaceholder() {
    assertThat(browse.entities()).hasSize(12);
    assertThat(placeholder(browse.entities(), "shift")).isTrue();
    assertThat(placeholder(browse.entities(), "sale_item")).isFalse();
    assertThat(placeholder(browse.entities(), "payment")).isFalse();
    assertThat(placeholder(browse.entities(), "deleted_sale")).isFalse();
    assertThat(placeholder(browse.entities(), "invoice")).isFalse();
  }

  @Test
  void dailySalesRowsCarryCommonColumnsFirstThenEntityColumns() {
    LocalDate date = LocalDate.of(2026, 9, 13);
    dailySalesService.record(
        new DailySalesInput("CTB", date, bd("100.00"), bd("9.00"), bd("91.00"), rawRecord("CTB")));

    DataPage<GenericRow> page = browse.listRows("daily_sales", 0, 10);

    assertThat(page.total()).isEqualTo(1);
    GenericRow row = page.items().get(0);
    assertThat(row.columns().keySet())
        .startsWith(
            "id", "logical_entity_id", "source_system", "source_record_ref", "raw_record_id");
    assertThat(row.columns()).containsEntry("source_system", "CTB");
    assertThat(row.columns()).containsEntry("trading_date", "2026-09-13");
    assertThat(row.columns()).containsEntry("total_sales", "100.0000");
    assertThat(row.columns()).containsEntry("gst_total", "9.0000");
    assertThat(row.columns()).containsEntry("net_total", "91.0000");
  }

  @Test
  void invoiceRowsCarryInvoiceColumns() {
    UUID raw = rawRecord("CTB");
    invoiceIngest.record(
        new InvoiceInput(
            "CTB",
            "INV-1",
            "Acme Supplies",
            LocalDate.of(2026, 9, 1),
            null,
            bd("250.50"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "acme-1.pdf",
            raw));

    DataPage<GenericRow> page = browse.listRows("invoice", 0, 10);

    assertThat(page.total()).isEqualTo(1);
    GenericRow row = page.items().get(0);
    assertThat(row.columns()).containsEntry("invoice_number", "INV-1");
    assertThat(row.columns()).containsEntry("supplier_name", "Acme Supplies");
    assertThat(row.columns()).containsEntry("invoice_date", "2026-09-01");
    assertThat(row.columns()).containsEntry("total_amount", "250.5000");
    assertThat(row.columns()).containsEntry("pdf_filename", "acme-1.pdf");
    assertThat(row.columns()).doesNotContainKey("due_date");
  }

  @Test
  void unknownEntityThrows() {
    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class, () -> browse.listRows("nope", 0, 10)))
        .hasMessageContaining("nope");
  }

  private static boolean placeholder(Iterable<EntityDescriptor> entities, String id) {
    for (EntityDescriptor d : entities) {
      if (d.id().equals(id)) {
        return d.placeholder();
      }
    }
    throw new AssertionError("no descriptor " + id);
  }

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
            + "values (?, ?, ?, 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
