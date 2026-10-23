package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.canonical.CanonicalProductSalesQuery;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.query.RelationalQueryService;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Historical replay of retained {@code ctb-revenue} sale-item raw records into {@code
 * source_product_sale_fact}: field retention, date/window association, re-pull dedup and
 * supersession, distinct-group preservation, null/zero handling, resolved-projection isolation,
 * queryability, authorization, provenance and idempotency.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SourceProductSaleFactBackfillIntegrationTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final UserRole STAFF =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));

  @Autowired JdbcTemplate jdbc;
  @Autowired RawLedgerQuery rawLedger;
  @Autowired CtbSaleItemParser parser;
  @Autowired SourceProductSaleFactIngest ingest;
  @Autowired CanonicalProductSalesQuery productSales;
  @Autowired RelationalQueryService queries;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table source_product_sale_observation, source_product_sale_fact, product_sales_contributor, canonical_product_sales, resolved_product_sales");
  }

  private void replay() {
    new SourceProductSaleFactBackfillRunner(rawLedger, parser, ingest, productSales).run(null);
  }

  @Test
  void completeReportsReplaceLegacyAndRenumberedRowsWithoutLosingHistoryOrReplayingOlderValues() {
    var day = java.time.LocalDate.of(2026, 8, 6);
    String legacy =
        "{\"data\":[{\"stockCode\":\"A\",\"stockDescription\":\"Old name\",\"siteNumber\":-1,\"quantitySold\":14,\"amount\":136.2796}]}";
    UUID old = rawRecord("sale-items:" + day, legacy, Instant.parse("2026-10-20T04:00:00Z"));
    var legacyFact =
        parser.parse(legacy.getBytes(StandardCharsets.UTF_8)).getFirst().sourceFact(day, old);
    ingest.record(legacyFact);
    String report =
        "{\"totalCount\":2,\"data\":[{\"uniqueId\":33,\"stockCode\":\"A\",\"stockDescription\":\"Old name\",\"siteNumber\":-1,\"quantitySold\":14,\"amount\":136.2796},{\"uniqueId\":34,\"stockCode\":\"A\",\"stockDescription\":\"New name\",\"siteNumber\":-1,\"quantitySold\":1,\"amount\":10.3205}]}";
    UUID first = rawRecord("sale-items:" + day, report, Instant.parse("2026-10-21T04:00:00Z"));
    var batch = new CtbProductSalesBatch(day);
    parser.parse(report.getBytes(StandardCharsets.UTF_8)).forEach(i -> batch.add(i, first));
    ingest.recordReport(batch.sourceFacts(), first);
    ingest.recordReport(batch.sourceFacts(), first);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where superseded_at is null",
                Integer.class))
        .isEqualTo(2);
    String renumbered =
        report
            .replace("\"uniqueId\":33", "\"uniqueId\":99")
            .replace("\"uniqueId\":34", "\"uniqueId\":100");
    UUID second = rawRecord("sale-items:" + day, renumbered, Instant.parse("2026-10-22T04:00:00Z"));
    var next = new CtbProductSalesBatch(day);
    parser.parse(renumbered.getBytes(StandardCharsets.UTF_8)).forEach(i -> next.add(i, second));
    ingest.recordReport(next.sourceFacts(), second);
    ingest.recordReport(batch.sourceFacts(), first);
    ingest.record(legacyFact);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where superseded_at is null",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select sum(amount) from source_product_sale_fact where superseded_at is null",
                BigDecimal.class))
        .isEqualByComparingTo("146.6001");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where superseded_at is not null",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForList(
                "select distinct raw_record_id from source_product_sale_fact where superseded_at is null",
                UUID.class))
        .containsExactly(second);
  }

  @Test
  void replayAcceptsTheCompleteRetainedCollisionButRejectsAnIncompleteNativeReport()
      throws Exception {
    String report;
    try (var in = getClass().getResourceAsStream("/fixtures/ctb/sale-items-row-collision.json")) {
      report = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var ledger = org.mockito.Mockito.mock(RawLedgerQuery.class);
    UUID first = rawRecord("sale-items:2026-08-06", report, Instant.parse("2026-10-21T04:00:00Z"));
    var firstRef =
        new RawLedgerQuery.RawRecordRef(
            first, "sale-items:2026-08-06", Instant.parse("2026-10-21T04:00:00Z"));
    org.mockito.Mockito.when(ledger.rawRecordRefsForFetcher("CTB", "ctb-revenue"))
        .thenReturn(java.util.List.of(firstRef));
    org.mockito.Mockito.when(ledger.payloadBytes(first))
        .thenReturn(report.getBytes(StandardCharsets.UTF_8));
    var runner = new SourceProductSaleFactBackfillRunner(ledger, parser, ingest, productSales);
    runner.replay();
    runner.replay();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where superseded_at is null",
                Integer.class))
        .isEqualTo(118);
    UUID second =
        rawRecord(
            "sale-items:2026-08-06",
            report.replace("\"totalCount\": 118", "\"totalCount\": 200"),
            Instant.parse("2026-10-22T04:00:00Z"));
    org.mockito.Mockito.when(ledger.rawRecordRefsForFetcher("CTB", "ctb-revenue"))
        .thenReturn(
            java.util.List.of(
                firstRef,
                new RawLedgerQuery.RawRecordRef(
                    second, "sale-items:2026-08-06", Instant.parse("2026-10-22T04:00:00Z"))));
    org.mockito.Mockito.when(ledger.payloadBytes(second))
        .thenReturn(
            report
                .replace("\"totalCount\": 118", "\"totalCount\": 200")
                .getBytes(StandardCharsets.UTF_8));
    var result = runner.replay();
    assertThat(result.rejected()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where superseded_at is null",
                Integer.class))
        .isEqualTo(118);
  }

  private UUID rawRecord(String sourceRecordRef, String json, Instant fetchedAt) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, "
            + "payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at, source_record_ref) "
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'ctb-revenue', ?, ?)",
        recordId,
        runId,
        bytes,
        "0".repeat(64),
        bytes.length,
        java.sql.Timestamp.from(fetchedAt),
        sourceRecordRef);
    return recordId;
  }

  private static String page(String code, String group, String unitPrice, String totalFoodCost) {
    String groupJson = group == null ? "null" : "\"" + group + "\"";
    return "{\"data\":[{\"stockCode\":\""
        + code
        + "\",\"stockDescription\":\"Beer\","
        + "\"quantitySold\":3,\"amount\":120.66,\"costPerUnit\":12.50,\"foodCost\":11.00,"
        + "\"totalFoodCost\":"
        + totalFoodCost
        + ",\"profit\":48.00,\"discountedAmount\":10.00,"
        + "\"taxAmount\":22.73,\"unitPrice\":"
        + unitPrice
        + ",\"uniqueGroupId\":"
        + groupJson
        + "}]}";
  }

  private int facts(String code) {
    return jdbc.queryForObject(
        "select count(*) from source_product_sale_fact where stock_code = ? and superseded_at is null",
        Integer.class,
        code);
  }

  private int versions(String code) {
    return jdbc.queryForObject(
        "select count(*) from source_product_sale_fact where stock_code = ?", Integer.class, code);
  }

  private void canonicalProductSale(UUID rawRecordId, String date, String productKey) {
    jdbc.update(
        "insert into canonical_product_sales (id, logical_entity_id, trading_date, product_name_key, "
            + "source_system, source_record_ref, raw_record_id, quantity_sold, amount, stock_code, "
            + "valid_from, recorded_at) "
            + "values (?, ?, ?, ?, 'CTB', ?, ?, 1, 10.00, null, now(), now())",
        UUID.randomUUID(),
        UUID.randomUUID(),
        Date.valueOf(date),
        productKey,
        productKey + "@" + date,
        rawRecordId);
  }

  @Test
  void replaysHistoricalSourceFieldsWithWindowDateAndProvenance() {
    UUID raw =
        rawRecord(
            "sale-items:2026-09-14",
            page("STK-HIST-1", "G-1", "13.75", "250.00"),
            Instant.parse("2026-09-14T04:00:00Z"));

    replay();

    assertThat(
            jdbc.queryForMap(
                "select trading_date, stock_code, cost_per_unit, food_cost, total_food_cost, "
                    + "profit, discounted_amount, tax_amount, unit_price, unique_group_id, raw_record_id "
                    + "from source_product_sale_fact where stock_code = 'STK-HIST-1'"))
        .containsEntry("trading_date", Date.valueOf("2026-09-14"))
        .containsEntry("stock_code", "STK-HIST-1")
        .containsEntry("cost_per_unit", new BigDecimal("12.5000"))
        .containsEntry("food_cost", new BigDecimal("11.0000"))
        .containsEntry("total_food_cost", new BigDecimal("250.0000"))
        .containsEntry("profit", new BigDecimal("48.0000"))
        .containsEntry("discounted_amount", new BigDecimal("10.0000"))
        .containsEntry("tax_amount", new BigDecimal("22.7300"))
        .containsEntry("unit_price", new BigDecimal("13.7500"))
        .containsEntry("unique_group_id", "G-1")
        .containsEntry("raw_record_id", raw);
  }

  @Test
  void reviewReplayWithCorrectionsDoesNotManufactureNewHistory() {
    rawRecord(
        "sale-items:2026-09-14",
        page("REVIEW-HISTORY", "G", "13.75", "250"),
        Instant.parse("2026-09-14T04:00:00Z"));
    rawRecord(
        "sale-items:2026-09-14",
        page("REVIEW-HISTORY", "G", "13.75", "260"),
        Instant.parse("2026-09-15T04:00:00Z"));
    replay();
    int before = versions("REVIEW-HISTORY");
    replay();
    assertThat(versions("REVIEW-HISTORY")).isEqualTo(before);
  }

  @Test
  void reviewSalesOnlyGrantCannotReadDetailedSourceCosts() throws Exception {
    rawRecord(
        "sale-items:2026-09-14",
        page("REVIEW-PRIVATE", "G", "13.75", "250"),
        Instant.parse("2026-09-14T04:00:00Z"));
    replay();
    jdbc.update(
        "insert into permission(id,department,seniority,resource,can_read,can_write) values(gen_random_uuid(),'REVIEW_SALES','MANAGER','reconciliation.sales',true,false) on conflict(department,seniority,resource) do nothing");
    UserRole salesOnly =
        new UserRole(new DepartmentCode("REVIEW_SALES"), new SeniorityCode("MANAGER"));
    QueryPlan plan =
        mapper.readValue(
            "{\"version\":1,\"dataset\":\"source_product_sale_fact\",\"select\":[{\"field\":\"total_food_cost\"}],\"limit\":100}",
            QueryPlan.class);
    assertThatThrownBy(() -> queries.run(salesOnly, plan))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void unchangedIntermediateReceiptIsAlsoIdempotentAfterCorrection() {
    rawRecord(
        "sale-items:2026-09-14",
        page("INTERMEDIATE", "G", "13.75", "250"),
        Instant.parse("2026-09-14T04:00:00Z"));
    rawRecord(
        "sale-items:2026-09-14",
        page("INTERMEDIATE", "G", "13.75", "250"),
        Instant.parse("2026-09-15T04:00:00Z"));
    rawRecord(
        "sale-items:2026-09-14",
        page("INTERMEDIATE", "G", "13.75", "260"),
        Instant.parse("2026-09-16T04:00:00Z"));
    replay();
    replay();
    assertThat(versions("INTERMEDIATE")).isEqualTo(2);
    assertThat(facts("INTERMEDIATE")).isEqualTo(1);
  }

  @Test
  void repeatedPullDoesNotDoubleCount() {
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-DEDUP-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-DEDUP-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-15T04:00:00Z"));

    replay();

    assertThat(facts("STK-DEDUP-1")).isEqualTo(1);
  }

  @Test
  void laterPullSupersedesEarlierVersion() {
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-SUP-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-SUP-1", "G-1", "13.75", "260.00"),
        Instant.parse("2026-09-15T04:00:00Z"));

    replay();

    assertThat(versions("STK-SUP-1")).isEqualTo(2);
    assertThat(facts("STK-SUP-1")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select total_food_cost from source_product_sale_fact "
                    + "where stock_code = 'STK-SUP-1' and superseded_at is null",
                BigDecimal.class))
        .isEqualByComparingTo("260.0000");
  }

  @Test
  void distinctSourceGroupsArePreservedWhenCodeAndDayCoincide() {
    String twoGroups =
        "{\"data\":["
            + "{\"stockCode\":\"STK-GRP-1\",\"stockDescription\":\"Wine\",\"quantitySold\":3,"
            + "\"amount\":120.66,\"costPerUnit\":12.50,\"foodCost\":11.00,\"totalFoodCost\":250.00,"
            + "\"profit\":48.00,\"discountedAmount\":10.00,\"taxAmount\":22.73,\"unitPrice\":13.75,"
            + "\"uniqueGroupId\":\"G-1\"},"
            + "{\"stockCode\":\"STK-GRP-1\",\"stockDescription\":\"Wine\",\"quantitySold\":2,"
            + "\"amount\":80.00,\"costPerUnit\":12.50,\"foodCost\":11.00,\"totalFoodCost\":160.00,"
            + "\"profit\":30.00,\"discountedAmount\":5.00,\"taxAmount\":15.00,\"unitPrice\":15.00,"
            + "\"uniqueGroupId\":\"G-2\"}]}";
    rawRecord("sale-items:2026-09-14", twoGroups, Instant.parse("2026-09-14T04:00:00Z"));

    replay();

    assertThat(facts("STK-GRP-1")).isEqualTo(2);
  }

  @Test
  void undatedRecordsAreAmbiguousAndBlankCodesRejected() {
    rawRecord(
        null, // undated: no durable trading date
        page("STK-AMB-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));
    String blankCode =
        "{\"data\":[{\"stockCode\":\"\",\"stockDescription\":\"Blank\",\"amount\":1.0}]}";
    rawRecord("sale-items:2026-09-14", blankCode, Instant.parse("2026-09-14T04:00:00Z"));

    replay();

    assertThat(facts("STK-AMB-1")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from source_product_sale_fact where stock_code = ''",
                Integer.class))
        .isZero();
  }

  @Test
  void recoversDateFromCanonicalProvenanceForUndatedRecords() {
    UUID raw =
        rawRecord(
            null, // undated raw record: no source-record reference (pre-fix)
            page("STK-CANON-1", "G-1", "13.75", "250.00"),
            Instant.parse("2026-09-14T04:00:00Z"));
    canonicalProductSale(raw, "2026-09-14", "beer");

    replay();

    assertThat(facts("STK-CANON-1")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select trading_date from source_product_sale_fact where stock_code = 'STK-CANON-1'",
                Date.class))
        .isEqualTo(Date.valueOf("2026-09-14"));
  }

  @Test
  void resolvedProductSalesTotalsAreUnchangedByReplay() {
    jdbc.update(
        "insert into resolved_product_sales (trading_date, product_name_key, quantity_sold, amount, "
            + "resolution_type, authoritative_source, has_conflict, resolved_at) "
            + "values (?, 'beer', 10, 80.00, 'rule', 'CTB', false, now())",
        Date.valueOf("2026-09-14"));

    rawRecord(
        "sale-items:2026-09-14",
        page("STK-RES-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));
    replay();

    assertThat(
            jdbc.queryForObject(
                "select count(*) from resolved_product_sales where product_name_key = 'beer'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select amount from resolved_product_sales where product_name_key = 'beer'",
                BigDecimal.class))
        .isEqualByComparingTo("80.00");
  }

  @Test
  void isQueryableThroughTheGenericInterfaceAndAuthorized() throws Exception {
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-QRY-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));
    replay();

    QueryPlan plan =
        mapper.readValue(
            "{\"version\":1,\"dataset\":\"source_product_sale_fact\","
                + "\"select\":[{\"field\":\"stock_code\"},{\"field\":\"total_food_cost\"}],"
                + "\"filter\":{\"predicate\":{\"field\":\"stock_code\",\"op\":\"EQ\",\"value\":\"STK-QRY-1\"}},"
                + "\"limit\":10}",
            QueryPlan.class);
    QueryResult result = queries.run(OWNER, plan);
    assertThat(result.rows()).hasSize(1);
    assertThat((BigDecimal) result.rows().get(0).get(1)).isEqualByComparingTo("250.0000");

    assertThatThrownBy(() -> queries.run(STAFF, plan)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void replayIsIdempotent() {
    rawRecord(
        "sale-items:2026-09-14",
        page("STK-IDEM-1", "G-1", "13.75", "250.00"),
        Instant.parse("2026-09-14T04:00:00Z"));

    replay();
    replay();

    assertThat(facts("STK-IDEM-1")).isEqualTo(1);
    assertThat(versions("STK-IDEM-1")).isEqualTo(1);
  }
}
