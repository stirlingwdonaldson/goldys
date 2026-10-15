package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.query.RelationalQueryService;
import com.goldys.platform.semantic.query.AggregateFunction;
import com.goldys.platform.semantic.query.GroupByItem;
import com.goldys.platform.semantic.query.OrderByItem;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.semantic.query.SelectItem;
import com.goldys.platform.semantic.query.SortDirection;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The legacy {@code invoice_logical_id} re-key backfill: proves that current lines ingested before
 * the parent-reference column existed are closed and re-inserted with their parent invoice id, that
 * orphans and ambiguous invoice numbers stay explicitly unresolved, that re-running is idempotent,
 * and that the {@code invoice → invoice_line} join then resolves correct supplier spend through the
 * generic query engine.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class InvoiceLogicalIdBackfillIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalInvoiceLineIngest lineIngest;
  @Autowired RelationalQueryService queries;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line, canonical_invoice");
  }

  @Test
  void backfillReKeysLegacyLineAndPreservesBitemporalHistory() {
    UUID invoiceLogicalId = invoice("Supplier A", "INV-1", "2026-10-01");
    String lineRef = legacyLine("INV-1", "2026-10-01", "beer", "STK-1", "100.00");

    int migrated = lineIngest.backfillInvoiceLogicalId();

    assertThat(migrated).isEqualTo(1);
    // The legacy version is closed; a successor carries the parent id and identical facts.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                Integer.class,
                lineRef))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                lineRef))
        .isEqualTo(invoiceLogicalId);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_invoice_line where source_record_ref = ? and superseded_at is not null",
                Integer.class,
                lineRef))
        .isEqualTo(1);
    // Fact and identity are unchanged — only the parent reference was added.
    assertThat(
            jdbc.queryForObject(
                "select line_total from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                BigDecimal.class,
                lineRef))
        .isEqualByComparingTo("100.0000");
    assertThat(
            jdbc.queryForObject(
                "select logical_entity_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                lineRef))
        .isEqualTo(
            jdbc.queryForObject(
                "select logical_entity_id from canonical_invoice_line where source_record_ref = ? and superseded_at is not null",
                UUID.class,
                lineRef));
  }

  @Test
  void orphanAndAmbiguousLinesStayExplicitlyUnresolved() {
    UUID invoiceA = invoice("Supplier A", "INV-1", "2026-10-01");
    UUID invoiceB = invoice("Supplier B", "INV-2", "2026-10-02");
    String inv1Line = legacyLine("INV-1", "2026-10-01", "beer", "STK-1", "100.00");
    String inv2Line = legacyLine("INV-2", "2026-10-02", "wine", "STK-2", "50.00");
    // A line with no current header (orphan), and two distinct invoices sharing one number
    // (ambiguous) — neither may be guessed.
    String orphan = legacyLine("ORPHAN", "2026-10-03", "bread", "STK-9", "5.00");
    invoice("Supplier C", "DUP", "2026-10-04");
    invoice("Supplier D", "DUP", "2026-10-05");
    String ambiguous = legacyLine("DUP", "2026-10-04", "salt", "STK-8", "2.00");

    int migrated = lineIngest.backfillInvoiceLogicalId();

    // Only the two unambiguous lines (INV-1, INV-2) are re-keyed.
    assertThat(migrated).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                inv1Line))
        .isEqualTo(invoiceA);
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                inv2Line))
        .isEqualTo(invoiceB);
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                orphan))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select invoice_logical_id from canonical_invoice_line where source_record_ref = ? and superseded_at is null",
                UUID.class,
                ambiguous))
        .isNull();
  }

  @Test
  void backfillIsIdempotent() {
    invoice("Supplier A", "INV-1", "2026-10-01");
    legacyLine("INV-1", "2026-10-01", "beer", "STK-1", "100.00");

    assertThat(lineIngest.backfillInvoiceLogicalId()).isEqualTo(1);
    // A second run finds no current line with a null parent id.
    assertThat(lineIngest.backfillInvoiceLogicalId()).isZero();
  }

  @Test
  void joinReturnsSupplierSpendAfterBackfill() {
    UUID inv1 = invoice("Supplier A", "INV-1", "2026-10-01");
    UUID inv2 = invoice("Supplier B", "INV-2", "2026-10-05");
    legacyLine("INV-1", "2026-10-01", "beer", "STK-1", "100.00");
    legacyLine("INV-1", "2026-10-01", "wine", "STK-2", "200.00");
    legacyLine("INV-2", "2026-10-05", "beer", "STK-1", "50.00");

    assertThat(lineIngest.backfillInvoiceLogicalId()).isEqualTo(3);

    QueryResult generic =
        queries.run(
            OWNER,
            new QueryPlan(
                1,
                "invoice",
                List.of("invoice_lines"),
                List.of(
                    new SelectItem("supplier_name", null, false),
                    new SelectItem("line_total", AggregateFunction.SUM, false)),
                null,
                List.of(new GroupByItem("supplier_name", null)),
                List.of(new OrderByItem("supplier_name", SortDirection.ASC)),
                100,
                0));

    List<Map<String, Object>> reference =
        jdbc.queryForList(
            "SELECT i.supplier_name, SUM(l.line_total) AS spend "
                + "FROM canonical_invoice i JOIN canonical_invoice_line l "
                + "ON i.logical_entity_id = l.invoice_logical_id "
                + "WHERE i.superseded_at IS NULL AND l.superseded_at IS NULL "
                + "GROUP BY i.supplier_name ORDER BY i.supplier_name");

    assertThat(generic.rows()).hasSize(2);
    assertThat(generic.rows().get(0).get(0)).isEqualTo("Supplier A");
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("spend"));
    assertThat((BigDecimal) generic.rows().get(1).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(1).get("spend"));
  }

  // ---- helpers ----

  /** Inserts a current invoice header and returns its stable logical id. */
  private UUID invoice(String supplier, String number, String date) {
    UUID logicalId = UUID.randomUUID();
    UUID raw = rawRecord();
    jdbc.update(
        "insert into canonical_invoice (id, logical_entity_id, source_system, source_record_ref, "
            + "raw_record_id, supplier_name, supplier_logical_id, supplier_source_ref, invoice_number, "
            + "invoice_date, amount_ex_tax, total_amount, valid_from, recorded_at) "
            + "values (?, ?, 'CTB', ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        logicalId,
        // Supplier-scoped source identity, matching the post-V74 key: two suppliers may share an
        // invoice number, so the source record ref disambiguates them.
        supplier + "::" + number,
        raw,
        supplier,
        UUID.randomUUID(),
        supplier,
        number,
        Date.valueOf(date),
        new BigDecimal("300"),
        new BigDecimal("330"));
    return logicalId;
  }

  /** Inserts a legacy line whose parent reference (and origin) are null, as pre-V74 rows are. */
  private String legacyLine(
      String number, String date, String product, String stockCode, String total) {
    String lineRef = number + ":line:" + stockCode + ":" + product;
    UUID raw = rawRecord();
    jdbc.update(
        "insert into canonical_invoice_line (id, logical_entity_id, source_system, source_record_ref, "
            + "raw_record_id, invoice_number, invoice_logical_id, invoice_date, product_name_key, "
            + "stock_code, quantity, unit_cost, line_total, origin, valid_from, recorded_at) "
            + "values (?, ?, 'CTB', ?, ?, ?, NULL, ?, ?, ?, 1, ?, ?, NULL, now(), now())",
        UUID.randomUUID(),
        UUID.randomUUID(),
        lineRef,
        raw,
        number,
        Date.valueOf(date),
        product,
        stockCode,
        new BigDecimal(total),
        new BigDecimal(total));
    return lineRef;
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
            + "values (?, ?, 'CTB', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'ctb-invoices', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
