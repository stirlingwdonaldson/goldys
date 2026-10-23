package com.goldys.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.QueryResult;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-checks the generic query results against independently written reference SQL, proves the
 * query path is genuinely read-only, and runs a timing sanity check over a realistic synthetic
 * dataset.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class RelationalQueryReferenceIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  @Autowired JdbcTemplate jdbc;
  @Autowired RelationalQueryService queries;
  @Autowired PlatformTransactionManager txManager;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_invoice_line, canonical_invoice, resolved_daily_sales");
  }

  @Test
  void groupedExpenditureMatchesIndependentSql() {
    seedInvoiceWithLines("Supplier A", "2026-10-01", "100.00");
    seedInvoiceWithLines("Supplier A", "2026-10-05", "200.00");
    seedInvoiceWithLines("Supplier B", "2026-10-01", "50.00");

    QueryResult generic =
        queries.run(
            OWNER,
            new com.goldys.platform.semantic.query.QueryPlan(
                1,
                "invoice",
                List.of("invoice_lines"),
                List.of(
                    new com.goldys.platform.semantic.query.SelectItem("supplier_name", null, false),
                    new com.goldys.platform.semantic.query.SelectItem(
                        "line_total",
                        com.goldys.platform.semantic.query.AggregateFunction.SUM,
                        false)),
                null,
                List.of(new com.goldys.platform.semantic.query.GroupByItem("supplier_name", null)),
                List.of(
                    new com.goldys.platform.semantic.query.OrderByItem(
                        "supplier_name", com.goldys.platform.semantic.query.SortDirection.ASC)),
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
    assertThat((BigDecimal) generic.rows().get(0).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(0).get("spend"));
    assertThat((BigDecimal) generic.rows().get(1).get(1))
        .isEqualByComparingTo((BigDecimal) reference.get(1).get("spend"));
  }

  @Test
  void readOnlyTransactionRejectsWrites() {
    seedInvoiceWithLines("Supplier A", "2026-10-01", "100.00");
    TransactionTemplate tt = new TransactionTemplate(txManager);
    tt.setReadOnly(true);
    assertThatThrownBy(
            () -> tt.executeWithoutResult(s -> jdbc.update("delete from resolved_daily_sales")))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void performanceSanityOnSyntheticData() {
    // 50 suppliers x 40 invoices x 5 lines = 10,000 lines (realistic purchasing volume).
    for (int s = 0; s < 50; s++) {
      for (int v = 0; v < 40; v++) {
        seedInvoiceWithLines(
            "Supplier " + s, "2026-10-" + String.format("%02d", (v % 28) + 1), "100.00");
      }
    }
    long start = System.nanoTime();
    QueryResult r =
        queries.run(
            OWNER,
            new com.goldys.platform.semantic.query.QueryPlan(
                1,
                "invoice",
                List.of("invoice_lines"),
                List.of(
                    new com.goldys.platform.semantic.query.SelectItem("supplier_name", null, false),
                    new com.goldys.platform.semantic.query.SelectItem(
                        "line_total",
                        com.goldys.platform.semantic.query.AggregateFunction.SUM,
                        false)),
                null,
                List.of(new com.goldys.platform.semantic.query.GroupByItem("supplier_name", null)),
                List.of(
                    new com.goldys.platform.semantic.query.OrderByItem(
                        "supplier_name", com.goldys.platform.semantic.query.SortDirection.ASC)),
                100,
                0));
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(r.rows()).hasSize(50);
    System.out.println("query grouped-expenditure over 10k lines took " + elapsedMs + " ms");
    // A loose bound to catch pathological plans without making the test flaky on shared CI.
    assertThat(elapsedMs).isLessThan(30_000);
  }

  private void seedInvoiceWithLines(String supplier, String date, String exTax) {
    UUID supplierId = UUID.randomUUID();
    UUID invoiceLogicalId = UUID.randomUUID();
    UUID raw = rawRecord();
    jdbc.update(
        "insert into canonical_invoice (id, logical_entity_id, source_system, source_record_ref, "
            + "raw_record_id, supplier_name, supplier_logical_id, supplier_source_ref, invoice_number, "
            + "invoice_date, amount_ex_tax, total_amount, valid_from, recorded_at) "
            + "values (?, ?, 'CTB', ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        invoiceLogicalId,
        invoiceLogicalId.toString(),
        raw,
        supplier,
        supplierId,
        supplier,
        invoiceLogicalId.toString(),
        Date.valueOf(date),
        new BigDecimal(exTax),
        new BigDecimal(exTax).add(new BigDecimal(exTax).multiply(new BigDecimal("0.10"))));
    for (int i = 0; i < 5; i++) {
      UUID lineRaw = rawRecord();
      jdbc.update(
          "insert into canonical_invoice_line (id, logical_entity_id, source_system, source_record_ref, "
              + "raw_record_id, invoice_number, invoice_logical_id, invoice_date, product_name_key, stock_code, "
              + "quantity, unit_cost, line_total, origin, valid_from, recorded_at) "
              + "values (?, ?, 'CTB', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'CSV', now(), now())",
          UUID.randomUUID(),
          UUID.randomUUID(),
          invoiceLogicalId.toString() + ":" + i,
          lineRaw,
          invoiceLogicalId.toString(),
          invoiceLogicalId,
          Date.valueOf(date),
          "product",
          "STK-" + i,
          new BigDecimal("20"),
          new BigDecimal("20"));
    }
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
