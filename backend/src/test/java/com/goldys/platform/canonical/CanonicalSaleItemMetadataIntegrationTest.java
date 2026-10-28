package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.connectors.lightspeed.LightspeedSaleItemIngestService;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalSaleItemMetadataIntegrationTest {
  @Autowired CanonicalSaleItemService canonical;
  @Autowired LightspeedSaleItemIngestService webhook;
  @Autowired IngestionService ingestion;
  @Autowired RawLedgerQuery raw;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void safeWebhookPreservesMetadataWhileUpdatingMoneyAndRetainingHistory() throws Exception {
    String ref = UUID.randomUUID().toString();
    var original = canonical.record(input("LIGHTSPEED", ref, "10", "receipt-1", "Example Staff"));
    byte[] delivery = body(ref, "20", false);
    webhook.ingest(delivery);
    var current = current(ref);
    assertThat(current.get("sale_number")).isEqualTo("receipt-1");
    assertThat(current.get("staff_name")).isEqualTo("Example Staff");
    assertThat((BigDecimal) current.get("amount")).isEqualByComparingTo("20");
    UUID rawId = (UUID) current.get("raw_record_id");
    assertThat(raw.payloadBytes(rawId)).containsExactly(delivery);
    assertThat(
            jdbc.queryForObject(
                "select superseded_at is not null from canonical_sale_item where id=?",
                Boolean.class,
                original.id()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select amount from canonical_sale_item where id=?",
                BigDecimal.class,
                original.id()))
        .isEqualByComparingTo("10");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ingestion_failure where ingestion_run_id=(select ingestion_run_id from raw_record where id=?) and failure_type='SALE_ITEM_METADATA_UNOBSERVED'",
                Integer.class,
                rawId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                rawId))
        .isEqualTo("PARTIAL");
    assertThat(
            jdbc.queryForObject(
                "select outcome from ingestion_stage where ingestion_run_id=(select ingestion_run_id from raw_record where id=?) and stage='CANONICALIZED'",
                String.class,
                rawId))
        .isEqualTo("SUCCESS");
    webhook.ingest(delivery);
    webhook.ingest(body(ref, "20", true));
    assertThat(current(ref).get("id")).isEqualTo(current.get("id"));
    assertThat(versions(ref)).isEqualTo(2);
    assertThat((BigDecimal) current.get("quantity_sold")).isEqualByComparingTo("2");
    assertThat((BigDecimal) current.get("total_tax")).isEqualByComparingTo("2");
    assertThat((BigDecimal) current.get("cost_inc_tax")).isEqualByComparingTo("3");
    webhook.ingest(
        new String(body(ref, "20", true), java.nio.charset.StandardCharsets.UTF_8)
            .replace(",   ", ",Example Changed Staff")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(current(ref).get("staff_name")).isEqualTo("Example Changed Staff");
    assertThat(current(ref).get("sale_number")).isEqualTo("receipt-1");
  }

  @Test
  void missingMetadataStaysUnknownUntilObservedAndBlankDoesNotClearIt() {
    String ref = UUID.randomUUID().toString();
    var unknown = canonical.record(input("LIGHTSPEED", ref, "10", "  ", null));
    assertThat(unknown.saleNumber()).isNull();
    assertThat(unknown.staffName()).isNull();
    canonical.record(input("LIGHTSPEED", ref, "10", "receipt-2", "Example New Staff"));
    canonical.record(input("LIGHTSPEED", ref, "10", null, "Example Updated Staff"));
    var unchanged = canonical.record(input("LIGHTSPEED", ref, "10", "", " \t"));
    assertThat(unchanged.saleNumber()).isEqualTo("receipt-2");
    assertThat(unchanged.staffName()).isEqualTo("Example Updated Staff");
    assertThat(versions(ref)).isEqualTo(3);
    var correction = canonical.record(input("LIGHTSPEED", ref, "10", "receipt-3", null));
    assertThat(correction.saleNumber()).isEqualTo("receipt-3");
    assertThat(correction.staffName()).isEqualTo("Example Updated Staff");
  }

  @Test
  void metadataRuleDoesNotChangeOtherSources() {
    String ref = UUID.randomUUID().toString();
    canonical.record(input("TEST_SOURCE", ref, "10", "receipt", "Example Staff"));
    var cleared = canonical.record(input("TEST_SOURCE", ref, "20", null, null));
    assertThat(cleared.saleNumber()).isNull();
    assertThat(cleared.staffName()).isNull();
  }

  @Test
  void concurrentFinancialUpdatePreservesMetadataFromLockedCurrentVersion() throws Exception {
    String ref = UUID.randomUUID().toString();
    canonical.record(input("LIGHTSPEED", ref, "10", "receipt", "Example Old Staff"));
    var updated = input("LIGHTSPEED", ref, "10", "receipt", "Example Current Staff");
    var missing = input("LIGHTSPEED", ref, "20", null, null);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var metadata =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            var result = canonical.record(updated);
                            locked.countDown();
                            await(release);
                            return result;
                          }));
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var financial =
            executor.submit(
                () -> {
                  started.countDown();
                  return canonical.record(missing);
                });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        metadata.get(20, TimeUnit.SECONDS);
        financial.get(20, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    assertThat(current(ref).get("staff_name")).isEqualTo("Example Current Staff");
    assertThat(current(ref).get("sale_number")).isEqualTo("receipt");
    assertThat((BigDecimal) current(ref).get("amount")).isEqualByComparingTo("20");
    assertThat(versions(ref)).isEqualTo(3);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS))
        throw new AssertionError("Timed out waiting for transaction");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private Map<String, Object> current(String ref) {
    return jdbc.queryForMap(
        "select * from canonical_sale_item where source_record_ref=? and superseded_at is null",
        ref);
  }

  private int versions(String ref) {
    return jdbc.queryForObject(
        "select count(*) from canonical_sale_item where source_record_ref=?", Integer.class, ref);
  }

  private SaleItemInput input(
      String source, String ref, String amount, String receipt, String staff) {
    UUID rawId =
        ingestion.ingestPush(
            source,
            "metadata-test",
            FetchMethod.FILE_EXPORT,
            "text/plain",
            "synthetic observation".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "UTF-8",
            "metadata-test");
    return new SaleItemInput(
        source,
        ref,
        LocalDate.of(2025, 1, 2),
        receipt,
        "Product",
        null,
        null,
        "Food",
        BigDecimal.ONE,
        new BigDecimal(amount),
        new BigDecimal(amount),
        BigDecimal.ONE,
        BigDecimal.ZERO,
        null,
        null,
        staff,
        null,
        null,
        rawId,
        null);
  }

  private byte[] body(String ref, String amount, boolean blankStaff) throws Exception {
    String csv =
        "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Products Product Name,Products POS Category Name,Sales Data Product Quantity,Sales Data Total Inc Tax,Advanced Dimensions Sold Price Inc Tax,Sales Data Total Tax,Sales Data Cost Inc Tax"
            + (blankStaff ? ",Staff Sale Closed Staff Name" : "")
            + "\n"
            + ref
            + ",2025-01-02,Product,Food,2,"
            + amount
            + ","
            + amount
            + ",2,3"
            + (blankStaff ? ",   " : "")
            + "\n";
    return new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
  }
}
