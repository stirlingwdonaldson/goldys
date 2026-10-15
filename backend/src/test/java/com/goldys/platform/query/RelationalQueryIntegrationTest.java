package com.goldys.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.QueryErrorCode;
import com.goldys.platform.semantic.query.QueryException;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The relational query backend, driven end-to-end through one generic interface: ten distinct
 * business questions are all expressed as the same {@link QueryPlan}, plus negative tests for
 * invalid fields, denied permissions, injection, fanout, ambiguity, limits and discovery.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class RelationalQueryIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));

  private static final UUID SUP_A = UUID.randomUUID();
  private static final UUID SUP_B = UUID.randomUUID();
  private static final UUID INV_1 = UUID.randomUUID();
  private static final UUID INV_2 = UUID.randomUUID();
  private static final UUID INV_3 = UUID.randomUUID();

  @Autowired JdbcTemplate jdbc;
  @Autowired RelationalQueryService queries;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void seed() {
    jdbc.update("truncate table canonical_invoice_line, canonical_invoice, resolved_daily_sales");
    // resolved daily sales
    sale("2026-10-01", "1000.00", "900.00", "100.00", "CTB");
    sale("2026-10-02", "1100.00", "990.00", "110.00", "CTB");
    sale("2026-10-03", "1200.00", "1080.00", "120.00", "LIGHTSPEED");
    // invoices
    invoice(INV_1, "INV-1", "Supplier A", SUP_A, "2026-10-01", "100.00");
    invoice(INV_2, "INV-2", "Supplier A", SUP_A, "2026-10-05", "200.00");
    invoice(INV_3, "INV-3", "Supplier B", SUP_B, "2026-11-15", "50.00");
    // invoice lines (join key = invoice logical id)
    line(INV_1, "INV-1", "2026-10-01", "STK-1", "beer", "100.00");
    line(INV_2, "INV-2", "2026-10-05", "STK-2", "wine", "200.00");
    line(INV_3, "INV-3", "2026-11-15", "STK-1", "beer", "50.00");
  }

  private QueryPlan plan(String json) throws Exception {
    return mapper.readValue(json, QueryPlan.class);
  }

  private QueryResult run(String json) throws Exception {
    return queries.run(OWNER, plan(json));
  }

  // ---- the ten business questions, all through the one generic interface ----

  @Test
  void groupedSupplierExpenditure() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"invoice_lines\"],"
                + "\"select\":[{\"field\":\"supplier_name\"},{\"field\":\"line_total\",\"aggregate\":\"SUM\"}],"
                + "\"groupBy\":[{\"field\":\"supplier_name\"}],\"orderBy\":[{\"field\":\"supplier_name\"}],"
                + "\"limit\":100}");
    assertThat((BigDecimal) row(r, "Supplier A")).isEqualByComparingTo("300");
    assertThat((BigDecimal) row(r, "Supplier B")).isEqualByComparingTo("50");
  }

  @Test
  void invoiceSpendByMonth() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"invoice_lines\"],"
                + "\"select\":[{\"field\":\"invoice.invoice_date\"},{\"field\":\"line_total\",\"aggregate\":\"SUM\"}],"
                + "\"groupBy\":[{\"field\":\"invoice.invoice_date\",\"grain\":\"MONTH\"}],"
                + "\"orderBy\":[{\"field\":\"invoice.invoice_date\"}],\"limit\":100}");
    assertThat(r.rows()).hasSize(2); // Oct + Nov
  }

  @Test
  void invoiceLineDetailFiltering() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice_line\","
                + "\"select\":[{\"field\":\"stock_code\"},{\"field\":\"line_total\"}],"
                + "\"filter\":{\"predicate\":{\"field\":\"stock_code\",\"op\":\"EQ\",\"value\":\"STK-1\"}},"
                + "\"limit\":100}");
    assertThat(r.rows()).hasSize(2);
    assertThat((BigDecimal) r.rows().get(0).get(1)).isEqualByComparingTo("100.0000");
  }

  @Test
  void averageInvoiceValue() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\","
                + "\"select\":[{\"field\":\"amount_ex_tax\",\"aggregate\":\"AVG\"}],\"limit\":10}");
    assertThat(r.rows()).hasSize(1);
    assertThat(((BigDecimal) r.rows().get(0).get(0)).doubleValue())
        .isCloseTo(116.667, within(0.001));
  }

  @Test
  void invoiceCountBySupplier() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\","
                + "\"select\":[{\"field\":\"supplier_name\"},{\"field\":\"invoice_number\",\"aggregate\":\"COUNT\"}],"
                + "\"groupBy\":[{\"field\":\"supplier_name\"}],\"limit\":100}");
    assertThat(row(r, "Supplier A")).isEqualTo(2L);
    assertThat(row(r, "Supplier B")).isEqualTo(1L);
  }

  @Test
  void purchasesByStockCode() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice_line\","
                + "\"select\":[{\"field\":\"stock_code\"},{\"field\":\"line_total\",\"aggregate\":\"SUM\"}],"
                + "\"groupBy\":[{\"field\":\"stock_code\"}],\"limit\":100}");
    assertThat((BigDecimal) row(r, "STK-1")).isEqualByComparingTo("150");
    assertThat((BigDecimal) row(r, "STK-2")).isEqualByComparingTo("200");
  }

  @Test
  void dailyResolvedRevenueTotals() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"resolved_daily_sales\","
                + "\"select\":[{\"field\":\"trading_date\"},{\"field\":\"total_sales\"}],"
                + "\"orderBy\":[{\"field\":\"trading_date\"}],\"limit\":100}");
    assertThat(r.rows()).hasSize(3);
    assertThat((BigDecimal) r.rows().get(0).get(1)).isEqualByComparingTo("1000.0000");
  }

  @Test
  void dateFilteredSalesTrend() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"resolved_daily_sales\","
                + "\"select\":[{\"field\":\"trading_date\"},{\"field\":\"total_sales\"}],"
                + "\"filter\":{\"predicate\":{\"field\":\"trading_date\",\"op\":\"BETWEEN\","
                + "\"value\":[\"2026-10-01\",\"2026-10-02\"]}},"
                + "\"orderBy\":[{\"field\":\"trading_date\"}],\"limit\":100}");
    assertThat(r.rows()).hasSize(2);
  }

  @Test
  void rankedPurchasingResults() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice_line\","
                + "\"select\":[{\"field\":\"stock_code\"},{\"field\":\"line_total\",\"aggregate\":\"SUM\"}],"
                + "\"groupBy\":[{\"field\":\"stock_code\"}],"
                + "\"orderBy\":[{\"field\":\"line_total\",\"direction\":\"DESC\"}],\"limit\":10}");
    assertThat(r.rows().get(0).get(0)).isEqualTo("STK-2"); // 200 > 150
  }

  @Test
  void joinedSupplierInvoiceLineQuery() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"invoice_lines\"],"
                + "\"select\":[{\"field\":\"supplier_name\"},{\"field\":\"invoice.invoice_number\"},"
                + "{\"field\":\"stock_code\"},{\"field\":\"line_total\"}],"
                + "\"orderBy\":[{\"field\":\"invoice.invoice_number\"}],\"limit\":100}");
    assertThat(r.rows()).hasSize(3);
  }

  // ---- negative tests ----

  @Test
  void unknownFieldIsRejected() {
    assertThatThrownBy(
            () ->
                run(
                    "{\"version\":1,\"dataset\":\"invoice\","
                        + "\"select\":[{\"field\":\"no_such_field\"}],\"limit\":10}"))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.UNKNOWN_FIELD);
  }

  @Test
  void deniedPermissionIsRejected() {
    assertThatThrownBy(
            () ->
                queries.run(
                    STAFF,
                    planOrThrow(
                        "{\"version\":1,\"dataset\":\"invoice\","
                            + "\"select\":[{\"field\":\"invoice_number\"}],\"limit\":10}")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void sqlInjectionIsRejected() {
    assertThatThrownBy(
            () ->
                run(
                    "{\"version\":1,\"dataset\":\"invoice\","
                        + "\"select\":[{\"field\":\"invoice_number; DROP TABLE canonical_invoice;--\"}],"
                        + "\"limit\":10}"))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.UNKNOWN_FIELD);
  }

  @Test
  void unknownRelationshipIsRejected() {
    assertThatThrownBy(
            () ->
                run(
                    "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"does_not_exist\"],"
                        + "\"select\":[{\"field\":\"invoice_number\"}],\"limit\":10}"))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.UNKNOWN_RELATIONSHIP);
  }

  @Test
  void joinFanoutIsRejected() {
    // SUM of a header measure across the 1:N join without distinct would double-count.
    assertThatThrownBy(
            () ->
                run(
                    "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"invoice_lines\"],"
                        + "\"select\":[{\"field\":\"total_amount\",\"aggregate\":\"SUM\"}],\"limit\":10}"))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.FANOUT_RISK);
  }

  @Test
  void fanoutJoinIsSafeWhenDistinct() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"invoice\",\"joins\":[\"invoice_lines\"],"
                + "\"select\":[{\"field\":\"total_amount\",\"aggregate\":\"SUM\",\"distinct\":true}],\"limit\":10}");
    assertThat(r.rows()).hasSize(1);
  }

  @Test
  void queryLimitIsEnforced() {
    assertThatThrownBy(
            () ->
                run(
                    "{\"version\":1,\"dataset\":\"invoice\","
                        + "\"select\":[{\"field\":\"invoice_number\"}],\"limit\":5000}"))
        .isInstanceOf(QueryException.class)
        .hasFieldOrPropertyWithValue("code", QueryErrorCode.LIMIT_EXCEEDED);
  }

  @Test
  void missingDataReturnsEmptyNotError() throws Exception {
    QueryResult r =
        run(
            "{\"version\":1,\"dataset\":\"resolved_daily_sales\","
                + "\"select\":[{\"field\":\"trading_date\"},{\"field\":\"total_sales\"}],"
                + "\"filter\":{\"predicate\":{\"field\":\"trading_date\",\"op\":\"EQ\",\"value\":\"1999-01-01\"}},"
                + "\"limit\":10}");
    assertThat(r.rows()).isEmpty();
  }

  @Test
  void unauthorizedDiscoveryIsHidden() {
    assertThat(queries.datasets(OWNER)).hasSize(11);
    assertThat(queries.datasets(STAFF)).isEmpty();
  }

  @Test
  void unauthorizedFieldDiscoveryIsDenied() {
    assertThatThrownBy(() -> queries.fields(STAFF, "invoice"))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ---- helpers ----

  private QueryPlan planOrThrow(String json) {
    try {
      return mapper.readValue(json, QueryPlan.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** The numeric value in the second column for the row whose first column equals {@code key}. */
  private Object row(QueryResult r, String key) {
    for (List<Object> row : r.rows()) {
      if (key.equals(String.valueOf(row.get(0)))) {
        return row.get(1);
      }
    }
    throw new AssertionError("no row for " + key + " in " + r.rows());
  }

  private void sale(String date, String total, String net, String gst, String source) {
    jdbc.update(
        "insert into resolved_daily_sales (trading_date, total_sales, net_sales, gst, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, ?, ?, ?, 'rule', ?, false, now())",
        java.sql.Date.valueOf(date),
        new BigDecimal(total),
        new BigDecimal(net),
        new BigDecimal(gst),
        source);
  }

  private void invoice(
      UUID logicalId, String number, String supplier, UUID supplierId, String date, String exTax) {
    UUID raw = rawRecord();
    jdbc.update(
        "insert into canonical_invoice (id, logical_entity_id, source_system, source_record_ref, "
            + "raw_record_id, supplier_name, supplier_logical_id, supplier_source_ref, invoice_number, "
            + "invoice_date, amount_ex_tax, total_amount, valid_from, recorded_at) "
            + "values (?, ?, 'CTB', ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        logicalId,
        number,
        raw,
        supplier,
        supplierId,
        supplier,
        number,
        java.sql.Date.valueOf(date),
        new BigDecimal(exTax),
        new BigDecimal(exTax).add(new BigDecimal(exTax).multiply(new BigDecimal("0.10"))));
  }

  private void line(
      UUID invoiceLogicalId,
      String number,
      String date,
      String stockCode,
      String product,
      String total) {
    UUID raw = rawRecord();
    jdbc.update(
        "insert into canonical_invoice_line (id, logical_entity_id, source_system, source_record_ref, "
            + "raw_record_id, invoice_number, invoice_logical_id, invoice_date, product_name_key, stock_code, "
            + "quantity, unit_cost, line_total, origin, valid_from, recorded_at) "
            + "values (?, ?, 'CTB', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'CSV', now(), now())",
        UUID.randomUUID(),
        UUID.randomUUID(),
        number + ":1",
        raw,
        number,
        invoiceLogicalId,
        java.sql.Date.valueOf(date),
        product,
        stockCode,
        new BigDecimal(total),
        new BigDecimal(total));
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
