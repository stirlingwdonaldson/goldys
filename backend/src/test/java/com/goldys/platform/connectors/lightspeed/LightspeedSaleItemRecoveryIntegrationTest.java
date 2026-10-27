package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalSaleItemQuery;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LightspeedSaleItemRecoveryIntegrationTest {
  @Autowired IngestionService ingestion;
  @Autowired RawLedgerQuery ledger;
  @Autowired LightspeedSaleItemIngestService service;
  @Autowired CanonicalSaleItemQuery canonical;
  @Autowired JdbcTemplate jdbc;

  @Test
  void unsafeReportPersistsRawEvidenceAndPartialFailureWithoutCanonicalWrites() throws Exception {
    String csv =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/test/resources/fixtures/lightspeed/sale-items-unsafe-sale-number.csv"));
    byte[] bytes = new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
    long versions = count("canonical_sale_item");
    assertThatThrownBy(() -> service.ingest(bytes))
        .hasFieldOrPropertyWithValue("failureType", "UNSAFE_REPORT_GRAIN");
    var id =
        jdbc.queryForObject(
            "select id from raw_record where fetcher_identity='lightspeed-sale-items' order by fetched_at desc limit 1",
            UUID.class);
    assertThat(ledger.payloadBytes(id)).containsExactly(bytes);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ingestion_stage where ingestion_run_id=(select ingestion_run_id from raw_record where id=?) and outcome='FAILED'",
                Integer.class,
                id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from ingestion_run where id=(select ingestion_run_id from raw_record where id=?)",
                String.class,
                id))
        .isEqualTo("PARTIAL");
    assertThat(count("canonical_sale_item")).isEqualTo(versions);
  }

  @Test
  void previewDoesNotWriteAndRecoveryPreservesNewerFactsAndIsRepeatable() throws Exception {
    String prefix = UUID.randomUUID().toString();
    UUID old = store(body(prefix, "10", true));
    service.ingest(body(prefix, "20", false)); // A later correction for the existing first line.
    long rawBefore = count("raw_record"), versionsBefore = count("canonical_sale_item");
    long stagesBefore = count("ingestion_stage");
    var preview = runner(old, true, 2).recover();
    assertThat(preview.missingBefore()).isEqualTo(1);
    assertThat(preview.inserted()).isZero();
    assertThat(count("canonical_sale_item")).isEqualTo(versionsBefore);
    assertThat(count("ingestion_stage")).isEqualTo(stagesBefore);
    var result = runner(old, false, 2).recover();
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.missingAfter()).isZero();
    assertThat(runner(old, false, 2).recover().inserted()).isZero();
    assertThat(count("canonical_sale_item")).isEqualTo(versionsBefore + 1);
    assertThat(count("raw_record")).isEqualTo(rawBefore);
    assertThat(
            jdbc.queryForObject(
                "select amount from canonical_sale_item where source_record_ref=? and superseded_at is null",
                java.math.BigDecimal.class,
                prefix + "-1"))
        .isEqualByComparingTo("20");
  }

  @Test
  void wrongManifestAndWrongDatasetFailBeforeWriting() throws Exception {
    UUID raw = store(body(UUID.randomUUID().toString(), "10", true));
    long versions = count("canonical_sale_item");
    assertThatThrownBy(() -> runner(raw, false, 3).recover()).hasMessageContaining("manifest");
    UUID wrong =
        ingestion.ingestPush(
            "CTB",
            "ctb-invoices",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            new byte[] {1},
            null,
            "ctb-invoices");
    assertThatThrownBy(() -> runner(wrong, false, 2).recover())
        .hasMessageContaining("not a Lightspeed");
    assertThat(count("canonical_sale_item")).isEqualTo(versions);
  }

  @Test
  void conflictingSourceKeysAreHeldAndNeedAnExplicitManifestBeforeWrites() throws Exception {
    String prefix = UUID.randomUUID().toString();
    byte[] original = body(prefix, "10", true);
    var mapper = new ObjectMapper();
    var tree = mapper.readTree(original);
    String csv =
        tree.path("attachment").path("data").asText()
            + prefix
            + "-1,2025-01-02,Product,Food,2,20\n";
    UUID raw = store(mapper.writeValueAsBytes(Map.of("attachment", Map.of("data", csv))));
    var preview = runner(raw, true, 2).recover();
    assertThat(preview.conflictingKeys()).isEqualTo(1);
    assertThat(preview.heldMissing()).isEqualTo(1);
    assertThat(preview.missingBefore()).isEqualTo(1);
    assertThatThrownBy(() -> runner(raw, false, 2).recover())
        .hasMessageContaining("Conflicting-key count");
    var result =
        new LightspeedSaleItemRecoveryRunner(
                ledger, service, canonical, ingestion, raw, false, 2, 1)
            .recover();
    assertThat(result.inserted()).isEqualTo(1);
    assertThat(result.heldMissing()).isEqualTo(1);
    assertThat(canonical.currentSourceRefs("LIGHTSPEED", java.util.List.of(prefix + "-1")))
        .isEmpty();
  }

  private LightspeedSaleItemRecoveryRunner runner(UUID id, boolean dryRun, long expected) {
    return new LightspeedSaleItemRecoveryRunner(
        ledger, service, canonical, ingestion, id, dryRun, expected, 0);
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private UUID store(byte[] body) {
    return ingestion.ingestPush(
        "LIGHTSPEED",
        "lightspeed-sale-items",
        FetchMethod.FILE_EXPORT,
        "application/json",
        body,
        "UTF-8",
        "lightspeed-sale-items");
  }

  private byte[] body(String prefix, String amount, boolean second) throws Exception {
    String csv =
        "Sales Data Receipt Line ID,Sales Data Sale Closed Date,Products Product Name,Products POS Category Name,Sales Data Product Quantity,Sales Data Total Inc Tax\n"
            + prefix
            + "-1,2025-01-02,Product,Food,1,"
            + amount
            + "\n"
            + (second ? prefix + "-2,2025-01-02,Product,Food,1,5\n" : "");
    return new ObjectMapper().writeValueAsBytes(Map.of("attachment", Map.of("data", csv)));
  }
}
