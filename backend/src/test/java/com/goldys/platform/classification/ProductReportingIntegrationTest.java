package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.semantic.ClassifiedWastageQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
@Transactional
class ProductReportingIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalWastageIngest ingest;
  @Autowired ClassifiedWastageQuery query;
  @Autowired EntityManager em;
  @Autowired ClassificationRunRepository runs;
  @Autowired com.goldys.platform.canonical.ClassificationEvidenceQuery evidence;
  private final LocalDate date = LocalDate.of(2026, 10, 1);

  @Test
  void wastage_keeps_item_evidence_and_effective_mapping_history_without_name_joins() {
    UUID raw = raw();
    // Exact real sample measures, synthetic line identity: no production record is modified.
    ingest.record(
        new WastageInput(
            "CTB",
            91001,
            91002,
            date,
            "braised beef cheek",
            new BigDecimal("0.0179"),
            new BigDecimal("329.2200"),
            null,
            null,
            "kilograms",
            null,
            440,
            raw));
    em.flush();
    var line = query.lines(date, date, 0, 100).getFirst();
    assertThat(line.salesNature()).isEqualTo("UNKNOWN");
    assertThat(line.recipeId()).isEqualTo(440);
    assertThat(line.sourceUnitCost()).isEqualByComparingTo("329.22");
    assertThat(line.quantity()).isEqualByComparingTo("0.0179");
    assertThat(line.reason()).isNull();
    assertThat(line.rawRecordId()).isEqualTo(raw);
    // A same-name sale classification is deliberately irrelevant to inventory identity.
    mapping("PRODUCT_NAME", "RESOLVED", "braised beef cheek", date, "BEVERAGE", "KEGS");
    mapping("CTB_RECIPE", "CTB", "440", date.plusDays(1), "FOOD", "MEAT");
    assertThat(query.lines(date, date, 0, 100).getFirst().salesNature()).isEqualTo("UNKNOWN");
    mapping("CTB_RECIPE", "CTB", "440", date, "FOOD", "MEAT");
    line = query.lines(date, date, 0, 100).getFirst();
    assertThat(line.salesNature()).isEqualTo("FOOD");
    assertThat(line.stockBucket()).isEqualTo("MEAT");
    assertThat(line.mappingScope()).isEqualTo("CTB_RECIPE");
    mapping("WASTE_LINE", "CTB", "wastage:91001:91002", date, "UNKNOWN", "UNKNOWN");
    assertThat(query.lines(date, date, 0, 100).getFirst().salesNature()).isEqualTo("UNKNOWN");
    assertThat(jdbc.queryForObject("select count(*) from product_reporting_mapping", Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from canonical_wastage where source_record_ref='wastage:91001:91002'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void source_evidence_queries_execute_on_the_current_postgres_schema() {
    assertThat(evidence.currentEvidence()).isNotNull();
  }

  @Test
  void run_snapshots_survive_a_fresh_entity_read() {
    var run = ClassificationRun.create(ClassificationRunKind.ASSIGN, java.time.Instant.now());
    run.snapshot("{\"businessContext\":\"historical cafe\"}", "[{\"status\":\"NEEDS_REVIEW\"}]");
    run.namingSnapshot("[]");
    runs.saveAndFlush(run);
    em.clear();
    var reread = runs.findById(run.id()).orElseThrow();
    assertThat(reread.contextSnapshot()).contains("historical cafe");
    assertThat(reread.proposalSnapshot()).contains("NEEDS_REVIEW");
    assertThat(reread.namingSnapshot()).isEqualTo("[]");
  }

  private void mapping(
      String scope, String source, String key, LocalDate effective, String nature, String bucket) {
    jdbc.update(
        """
      insert into product_reporting_mapping(scope,source_system,entity_key,effective_from,
        sales_nature,stock_bucket,item_role,business_area,lifecycle,reason,actor)
      values (?,?,?,?,?,?,'UNKNOWN','UNKNOWN','UNKNOWN','reviewed test evidence','test-owner')
      """,
        scope,
        source,
        key,
        effective,
        nature,
        bucket);
  }

  private UUID raw() {
    UUID run = UUID.randomUUID(), raw = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run(id,source_system,connector_name,status,started_at,fetched_count,persisted_count) values (?,'CTB','test','SUCCESS',now(),1,1)",
        run);
    jdbc.update(
        "insert into raw_record(id,ingestion_run_id,source_system,fetch_method,content_type,payload_bytes,payload_sha256,payload_byte_length,fetcher_identity,fetched_at) values (?,?,'CTB','API','application/json',?,?,1,'test',now())",
        raw,
        run,
        new byte[] {1},
        "0".repeat(64));
    return raw;
  }
}
