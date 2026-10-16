package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises supplier-scoped invoice identity and content-based line identity through the full CSV
 * ingest path. Two suppliers may issue the same invoice number, reordered lines keep their
 * identity, a removed line is superseded (not left stale), and a duplicated delivery is idempotent.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SupplierInvoiceIdentityIntegrationTest {

  private static final String HEADER =
      "Invoice,Supplier,SupplierCode,Date,StockCode,StockDescription,LineQuantity,LineTotalExTax";

  @Autowired JdbcTemplate jdbc;
  @Autowired CtInvoiceCsvIngestService csvIngest;
  @Autowired com.goldys.platform.canonical.CanonicalInvoiceLineIngest invoiceLines;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table stock_item, canonical_invoice_line, canonical_invoice, canonical_supplier, invoice_ingest_flag");
  }

  @Test
  void twoSuppliersSharingAnInvoiceNumberAreDistinct() {
    csvIngest.ingest(
        csv(
            row("INV-100", "Supplier A", "SUP-A", "2026-10-01", "STK-1", "Item A", "1", "10.00"),
            row("INV-100", "Supplier B", "SUP-B", "2026-10-01", "STK-2", "Item B", "1", "20.00")));

    assertThat(count("canonical_invoice")).isEqualTo(2);
    assertThat(count("canonical_supplier")).isEqualTo(2);
    // Two distinct suppliers, two distinct invoice logical ids — never merged on the shared number.
    assertThat(
            jdbc.queryForList(
                "select logical_entity_id from canonical_invoice where superseded_at is null",
                java.util.UUID.class))
        .hasSize(2);
  }

  @Test
  void reorderedLinesKeepTheirIdentity() {
    csvIngest.ingest(
        csv(
            row("INV-200", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00"),
            row("INV-200", "Supplier", "SUP", "2026-10-01", "STK-2", "Item B", "1", "20.00")));
    List<java.util.UUID> before = currentLineIds();

    csvIngest.ingest(
        csv(
            row("INV-200", "Supplier", "SUP", "2026-10-01", "STK-2", "Item B", "1", "20.00"),
            row("INV-200", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00")));

    // Same logical ids, no version churn from reordering.
    assertThat(currentLineIds()).containsExactlyInAnyOrderElementsOf(before);
    assertThat(totalLineVersions()).isEqualTo(2);
  }

  @Test
  void partialDeliveryKeepsAbsentLines() {
    csvIngest.ingest(
        csv(
            row("INV-300", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00"),
            row("INV-300", "Supplier", "SUP", "2026-10-01", "STK-2", "Item B", "1", "20.00")));

    csvIngest.ingest(
        csv(row("INV-300", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00")));

    assertThat(currentLineCount()).isEqualTo(2);
    assertThat(totalLineVersions()).isEqualTo(2); // A current + B superseded
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where superseded_at is not null",
                Integer.class))
        .isEqualTo(0);
  }

  @Test
  void duplicatedDeliveryIsIdempotent() {
    byte[] csv =
        csv(
            row("INV-400", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00"),
            row("INV-400", "Supplier", "SUP", "2026-10-01", "STK-2", "Item B", "1", "20.00"));

    csvIngest.ingest(csv);
    csvIngest.ingest(csv);

    assertThat(currentLineCount()).isEqualTo(2);
    assertThat(totalLineVersions()).isEqualTo(2); // no duplicate versions
    assertThat(count("canonical_invoice")).isEqualTo(1);
  }

  @Test
  void supplierRenamePreservesIdentity() {
    csvIngest.ingest(
        csv(row("INV-500", "Old Name", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00")));
    csvIngest.ingest(
        csv(
            row(
                "INV-500",
                "Renamed Supplier",
                "SUP",
                "2026-10-01",
                "STK-1",
                "Item A",
                "1",
                "10.00")));

    // One supplier entity (same code), whose name was superseded rather than a new supplier
    // created.
    assertThat(count("canonical_supplier")).isEqualTo(2); // two versions
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_supplier where superseded_at is null",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void invoiceLinesRecordSupplierScopedStockItems() {
    csvIngest.ingest(
        csv(row("INV-600", "Supplier", "SUP", "2026-10-01", "STK-1", "Item A", "1", "10.00")));

    // One supplier-scoped stock item per (supplier, stock code), with the supplier link populated.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from stock_item where superseded_at is null", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select supplier_logical_id is not null from stock_item where superseded_at is null",
                Boolean.class))
        .isTrue();
  }

  @Test
  void reviewLegacyLineIsNotDuplicatedByFirstScopedReplay() {
    byte[] bytes =
        csv(row("LEGACY", "Supplier", "SUP", "2026-10-01", "STK-1", "Item", "1", "10.00"));
    csvIngest.ingest(bytes);
    jdbc.update(
        "update canonical_invoice set source_record_ref=invoice_number, supplier_logical_id=null, supplier_source_ref=null where superseded_at is null");
    jdbc.update(
        "update canonical_invoice_line set source_record_ref=invoice_number||':1', invoice_logical_id=null where superseded_at is null");
    csvIngest.ingest(bytes);
    assertThat(currentLineCount()).isEqualTo(1);
  }

  @Test
  void reviewOtherSupplierPdfLinesAreNotSuperseded() {
    String header = HEADER + ",InvoiceTotalExTax";
    csvIngest.ingest(
        (header
                + "\n"
                + row("SAME", "Supplier A", "SUP-A", "2026-10-01", "A", "Item A", "1", "10.00")
                + ",100")
            .getBytes(StandardCharsets.UTF_8));
    java.util.UUID raw =
        jdbc.queryForObject(
            "select raw_record_id from canonical_invoice where superseded_at is null",
            java.util.UUID.class);
    java.util.UUID parent =
        jdbc.queryForObject(
            "select logical_entity_id from canonical_invoice where superseded_at is null",
            java.util.UUID.class);
    jdbc.update(
        "insert into canonical_invoice_line(id,logical_entity_id,source_system,source_record_ref,raw_record_id,invoice_number,invoice_logical_id,invoice_date,product_name_key,quantity,unit_cost,line_total,origin,valid_from,recorded_at) values(?,?,'CTB','review-pdf',?,'SAME',?,'2026-10-01','pdf-item',1,90,90,'PDF',now(),now())",
        java.util.UUID.randomUUID(),
        java.util.UUID.randomUUID(),
        raw,
        parent);
    csvIngest.ingest(
        (header
                + "\n"
                + row("SAME", "Supplier B", "SUP-B", "2026-10-01", "B", "Item B", "1", "20.00")
                + ",20")
            .getBytes(StandardCharsets.UTF_8));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where origin='PDF' and superseded_at is null",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void changedPartialDeliveryIsWithheldAtomicallyAndRawEvidenceRetained() {
    String header = HEADER + ",InvoiceTotalExTax";
    csvIngest.ingest(
        (header
                + "\n"
                + row("REV", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "10")
                + ",10")
            .getBytes(StandardCharsets.UTF_8));
    int rawBefore = count("raw_record");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                csvIngest.ingest(
                    (header
                            + "\n"
                            + row("REV", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "20")
                            + ",20")
                        .getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complete-versus-partial");
    assertThat(count("raw_record")).isEqualTo(rawBefore + 1);
    assertThat(currentLineCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select amount_ex_tax from canonical_invoice where superseded_at is null",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("10");
    assertThat(
            jdbc.queryForObject(
                "select line_total from canonical_invoice_line where superseded_at is null",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("10");
  }

  @Test
  void legacyTransitionAfterParentBackfillRetainsEnrichmentAndParentIdentity() {
    byte[] bytes = csv(row("OLD", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "10"));
    csvIngest.ingest(bytes);
    var parent =
        jdbc.queryForObject(
            "select logical_entity_id from canonical_invoice where superseded_at is null",
            java.util.UUID.class);
    jdbc.update(
        "update canonical_invoice set source_record_ref=invoice_number, supplier_logical_id=null, supplier_source_ref=null where superseded_at is null");
    jdbc.update(
        "update canonical_invoice_line set source_record_ref=invoice_number||':1', invoice_logical_id=null, uom='PACK', pack_size=6, line_gst_amount=1 where superseded_at is null");
    invoiceLines.backfillInvoiceLogicalId();
    csvIngest.ingest(bytes);
    assertThat(currentLineCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where superseded_at is null",
                java.util.UUID.class))
        .isEqualTo(parent);
    assertThat(
            jdbc.queryForObject(
                "select uom from canonical_invoice_line where superseded_at is null", String.class))
        .isEqualTo("PACK");
    assertThat(
            jdbc.queryForObject(
                "select line_gst_amount from canonical_invoice_line where superseded_at is null",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("1");
  }

  @Test
  void identicalRepeatedLinesRetainOccurrenceIdentitiesAcrossReplay() {
    String item = row("DUP", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "10");
    csvIngest.ingest(csv(item, item));
    var ids = currentLineIds();
    csvIngest.ingest(csv(item, item));
    assertThat(currentLineIds()).containsExactlyInAnyOrderElementsOf(ids);
    assertThat(totalLineVersions()).isEqualTo(2);
  }

  @Test
  void unchangedScopedCsvRetainsIndependentEnrichmentWithoutVersionChurn() {
    byte[] bytes = csv(row("ENRICHED", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "10"));
    csvIngest.ingest(bytes);
    jdbc.update(
        "update canonical_invoice_line set category='Food', uom='PACK', line_gst_amount=1 where superseded_at is null");
    csvIngest.ingest(bytes);
    assertThat(totalLineVersions()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select category from canonical_invoice_line where superseded_at is null",
                String.class))
        .isEqualTo("Food");
    assertThat(
            jdbc.queryForObject(
                "select line_gst_amount from canonical_invoice_line where superseded_at is null",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("1");
  }

  @Test
  void numericFormattingDoesNotChangeLineIdentityOrCreateRevision() {
    csvIngest.ingest(csv(row("FORMAT", "Supplier", "SUP", "2026-10-01", "A", "Item", "1", "10.0")));
    var ids = currentLineIds();
    csvIngest.ingest(
        csv(row("FORMAT", "Supplier", "SUP", "2026-10-01", "A", "Item", "1.000", "10.00")));
    assertThat(currentLineIds()).containsExactlyElementsOf(ids);
    assertThat(totalLineVersions()).isEqualTo(1);
  }

  private int currentLineCount() {
    return jdbc.queryForObject(
        "select count(*) from canonical_invoice_line where superseded_at is null", Integer.class);
  }

  private int totalLineVersions() {
    return jdbc.queryForObject("select count(*) from canonical_invoice_line", Integer.class);
  }

  private List<java.util.UUID> currentLineIds() {
    return jdbc.queryForList(
        "select logical_entity_id from canonical_invoice_line where superseded_at is null",
        java.util.UUID.class);
  }

  private int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private static byte[] csv(String... rows) {
    return (HEADER + "\n" + String.join("\n", rows)).getBytes(StandardCharsets.UTF_8);
  }

  private static String row(
      String invoice,
      String supplier,
      String supplierCode,
      String date,
      String stockCode,
      String description,
      String quantity,
      String lineTotal) {
    return String.join(
        ",", invoice, supplier, supplierCode, date, stockCode, description, quantity, lineTotal);
  }
}
