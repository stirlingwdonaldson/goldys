package com.goldys.platform.connectors.ctb;

import static org.assertj.core.api.Assertions.*;

import com.goldys.platform.application.CtbProductDetailRecoveryApplicationService;
import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.SourceProductSaleFactIngest;
import com.goldys.platform.canonical.SourceProductSaleFactRepair.State;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
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
class CtbProductDetailRecoveryIntegrationTest {
  private static final LocalDate DAY = LocalDate.of(2026, 7, 8);
  private static final String REPORT =
      """
      {"totalCount":1,"data":[{"stockCode":"A","stockDescription":"Parma",
      "quantitySold":3,"amount":75,"taxAmount":6.82,"unitPrice":25}]}
      """;
  @Autowired CtbProductDetailRecovery recovery;
  @Autowired CtbProductDetailRecoveryApplicationService application;
  @Autowired SourceProductSaleFactIngest ingest;
  @Autowired CtbSaleItemParser parser;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.execute(
        "truncate source_product_sale_observation, source_product_sale_fact, product_sales_contributor, canonical_product_sales, resolved_product_sales");
  }

  @Test
  void previewDoesNotWriteAndApplyPreservesProvenanceAndIsIdempotent() {
    var request = raw(REPORT, "sale-items:" + DAY);
    long raws = count("raw_record");
    long stages = count("ingestion_stage");
    var preview = recovery.preview(request);
    assertThat(preview).isEqualTo(recovery.preview(request));
    assertThat(preview.plan().snapshot().state()).isEqualTo(State.READY);
    assertThat(preview.plan().amount()).isEqualByComparingTo("75");
    assertThat(count("raw_record")).isEqualTo(raws);
    assertThat(count("ingestion_stage")).isEqualTo(stages);
    assertThat(count("source_product_sale_fact")).isZero();
    var applied = recovery.apply(request, preview.sha256());
    assertThat(applied.inserted()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("select raw_record_id from source_product_sale_fact", UUID.class))
        .isEqualTo(request.rawRecordId());
    assertThat(count("canonical_product_sales")).isZero();
    assertThat(count("resolved_product_sales")).isZero();
    var fresh = recovery.preview(request);
    assertThat(fresh.plan().snapshot().state()).isEqualTo(State.ALREADY_PRESENT);
    assertThat(recovery.apply(request, fresh.sha256()).inserted()).isZero();
    assertThat(count("source_product_sale_fact")).isEqualTo(1);
    assertThatThrownBy(() -> recovery.apply(request, preview.sha256()))
        .hasMessageContaining("preview changed");
  }

  @Test
  void existingStoragePrecisionIsExplicitAndDoesNotBreakRetryRecognition() {
    var report =
        REPORT.replace("\"unitPrice\":25", "\"unitPrice\":1.234567,\"totalFoodCost\":-1.23455");
    var request = raw(report, "sale-items:" + DAY);
    var preview = recovery.preview(request);
    assertThat(preview.plan().storageBasis())
        .contains("numeric(14,4)", "raw bytes retain full precision");
    recovery.apply(request, preview.sha256());
    assertThat(
            jdbc.queryForObject(
                "select unit_price from source_product_sale_fact", java.math.BigDecimal.class))
        .isEqualByComparingTo("1.2346");
    assertThat(
            jdbc.queryForObject(
                "select total_food_cost from source_product_sale_fact", java.math.BigDecimal.class))
        .isEqualByComparingTo("-1.2346");
    var retry = recovery.preview(request);
    assertThat(retry.plan().snapshot().state()).isEqualTo(State.ALREADY_PRESENT);
    assertThat(recovery.apply(request, retry.sha256()).inserted()).isZero();
    assertThat(
            jdbc.queryForObject(
                "select payload_sha256 from raw_record where id=?",
                String.class,
                request.rawRecordId()))
        .isEqualTo(request.payloadSha256());
  }

  @Test
  void existingOrConcurrentDetailHoldsRecoveryWithoutOverwriting() {
    var request = raw(REPORT, "sale-items:" + DAY);
    var preview = recovery.preview(request);
    var newer = raw(REPORT.replace("75", "90"), "sale-items:" + DAY);
    ingest.record(
        parser
            .parse(REPORT.replace("75", "90").getBytes(StandardCharsets.UTF_8))
            .getFirst()
            .sourceFact(DAY, newer.rawRecordId()));
    assertThatThrownBy(() -> recovery.apply(request, preview.sha256()))
        .hasMessageContaining("preview changed");
    var held = recovery.preview(request);
    assertThat(held.plan().snapshot().state()).isEqualTo(State.HELD);
    assertThatThrownBy(() -> recovery.apply(request, held.sha256())).hasMessageContaining("held");
    assertThat(count("source_product_sale_fact")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select amount from source_product_sale_fact", java.math.BigDecimal.class))
        .isEqualByComparingTo("90");
  }

  @Test
  void refusesIncompletePagesAmbiguousDatesWrongSourcesAndDigests() {
    var partial = raw(REPORT.replace("\"totalCount\":1", "\"totalCount\":2"), "sale-items:" + DAY);
    assertThatThrownBy(() -> recovery.preview(partial))
        .hasMessageContaining("complete single-page");
    var ambiguous = raw(REPORT, null);
    assertThatThrownBy(() -> recovery.preview(ambiguous))
        .hasMessageContaining("durable trading date");
    var request = raw(REPORT, "sale-items:" + DAY);
    assertThatThrownBy(
            () ->
                recovery.preview(
                    new CtbProductDetailRecovery.Request(request.rawRecordId(), "0".repeat(64))))
        .hasMessageContaining("checksum");
    var wrongSource = raw(REPORT, "sale-items:" + DAY, "LIGHTSPEED");
    assertThatThrownBy(() -> recovery.preview(wrongSource)).hasMessageContaining("retained CTB");
    assertThat(count("source_product_sale_fact")).isZero();
  }

  @Test
  void requiresMaintenanceGrantBeforeAccessingRetainedData() {
    var staff = new UserRole(new DepartmentCode("ALL"), new SeniorityCode("STAFF"));
    var request = new CtbProductDetailRecovery.Request(UUID.randomUUID(), "0".repeat(64));
    assertThatThrownBy(() -> application.preview(staff, request))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> application.apply(staff, request, "0".repeat(64)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void acceptsRealRetainedRowCollisionFixtureAndRejectsPartialVersion() throws Exception {
    String report;
    try (var in = getClass().getResourceAsStream("/fixtures/ctb/sale-items-row-collision.json")) {
      report = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var request = raw(report, "sale-items:" + DAY);
    var preview = recovery.preview(request);
    assertThat(preview.plan().facts()).isEqualTo(118);
    assertThat(recovery.apply(request, preview.sha256()).inserted()).isEqualTo(118);
    assertThat(count("source_product_sale_fact")).isEqualTo(118);
  }

  @Test
  void historicalProvenanceDatesLegacyRecordsWithoutChangingTotals() {
    var request = raw(REPORT, null);
    jdbc.update(
        """
        insert into canonical_product_sales(id, logical_entity_id, trading_date, product_name_key,
        source_system, source_record_ref, raw_record_id, quantity_sold, amount, valid_from, recorded_at)
        values (?, ?, ?, 'parma', 'CTB', 'parma@2026-07-08', ?, 3, 75, now(), now())
        """,
        UUID.randomUUID(),
        UUID.randomUUID(),
        java.sql.Date.valueOf(DAY),
        request.rawRecordId());
    var preview = recovery.preview(request);
    assertThat(preview.plan().day()).isEqualTo(DAY);
    assertThat(recovery.apply(request, preview.sha256()).inserted()).isEqualTo(1);
    assertThat(count("canonical_product_sales")).isEqualTo(1);
    assertThat(count("resolved_product_sales")).isZero();
  }

  @Test
  void priorObservationThatWouldSilentlySkipAnInsertRollsBackAndRecordsFailure() {
    var request = raw(REPORT, "sale-items:" + DAY);
    jdbc.update(
        "insert into source_product_sale_observation(source_system,source_record_ref,raw_record_id) values ('CTB',?,?)",
        "A@" + DAY,
        request.rawRecordId());
    var preview = recovery.preview(request);
    assertThatThrownBy(() -> recovery.apply(request, preview.sha256()))
        .hasMessageContaining("every expected fact");
    assertThat(count("source_product_sale_fact")).isZero();
    assertThat(
            jdbc.queryForObject(
                """
        select count(*) from ingestion_stage s join raw_record r on r.ingestion_run_id=s.ingestion_run_id
        where r.fetcher_identity='ctb-detail-recovery-plan' and s.outcome='FAILED'
        """,
                Long.class))
        .isGreaterThan(0);
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private CtbProductDetailRecovery.Request raw(String report, String ref) {
    return raw(report, ref, "CTB");
  }

  private CtbProductDetailRecovery.Request raw(String report, String ref, String source) {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
    String sha = CtbProductDetailRecovery.sha256(bytes);
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) values (?, 'CTB', 'test', 'SUCCESS', now(), 1, 1)",
        run);
    jdbc.update(
        """
        insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type,
        payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at, source_record_ref)
        values (?, ?, ?, 'API', 'application/json', ?, ?, ?, 'ctb-revenue', now(), ?)
        """,
        raw,
        run,
        source,
        bytes,
        sha,
        bytes.length,
        ref);
    return new CtbProductDetailRecovery.Request(raw, sha);
  }
}
