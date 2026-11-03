package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.goldys.platform.canonical.CanonicalWastageIngest;
import com.goldys.platform.canonical.WastageInput;
import com.goldys.platform.semantic.ClassifiedWastageQuery;
import com.goldys.platform.support.PostgresContainerConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
  @Autowired ProductReportingMappingService mappingService;
  @Autowired ProductClassificationService classification;

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

  @Autowired com.goldys.platform.semantic.ReportingSalesQuery reportingSales;
  @Autowired com.goldys.platform.semantic.WeeklyInventoryQuery weeklyInventory;

  @Test
  void reporting_tax_requires_selected_source_matching_gross_and_quantity_and_mapping_date() {
    UUID raw = raw();
    jdbc.update(
        """
      insert into resolved_product_sales(trading_date,product_name_key,quantity_sold,amount,resolution_type,authoritative_source,has_conflict,resolved_at)
      values (?,'historical cafe steak',2,110,'rule','CTB',false,now())
      """,
        date);
    jdbc.update(
        """
      insert into source_product_sale_fact(id,logical_entity_id,source_system,source_record_ref,raw_record_id,
      valid_from,recorded_at,trading_date,stock_code,product_name,quantity_sold,amount,tax_amount,site_number)
      values (?,?,'CTB','tax-test',?,now(),now(),?,'STEAK','New Historical Cafe Steak',2,110,10,0)
      """,
        UUID.randomUUID(),
        UUID.randomUUID(),
        raw,
        date);
    mapping(
        "PRODUCT_NAME",
        "RESOLVED",
        "historical cafe steak",
        date.plusDays(1),
        "BEVERAGE",
        "UNKNOWN");
    var row = reportingSales.sales(date, date).getFirst();
    assertThat(row.net()).isEqualByComparingTo("100");
    jdbc.update(
        "update source_product_sale_fact set site_number=null where source_record_ref='tax-test'");
    assertThat(reportingSales.sales(date, date).getFirst().net()).isNull();
    jdbc.update(
        "update source_product_sale_fact set site_number=0 where source_record_ref='tax-test'");
    assertThat(row.nature()).isEqualTo("UNKNOWN");
    mapping("PRODUCT_NAME", "RESOLVED", "historical cafe steak", date, "FOOD", "UNKNOWN");
    assertThat(reportingSales.sales(date, date).getFirst().nature()).isEqualTo("FOOD");
    mapping("PRODUCT_NAME", "RESOLVED", "historical cafe steak", date, "UNKNOWN", "UNKNOWN");
    assertThat(reportingSales.sales(date, date).getFirst().nature()).isEqualTo("UNKNOWN");
    jdbc.update(
        "update resolved_product_sales set authoritative_source='LIGHTSPEED' where product_name_key='historical cafe steak'");
    assertThat(reportingSales.sales(date, date).getFirst().net()).isNull();
    jdbc.update(
        "update resolved_product_sales set authoritative_source='CTB',amount=111 where product_name_key='historical cafe steak'");
    assertThat(reportingSales.sales(date, date).getFirst().net()).isNull();
    jdbc.update(
        "update resolved_product_sales set amount=110,quantity_sold=3 where product_name_key='historical cafe steak'");
    assertThat(reportingSales.sales(date, date).getFirst().net()).isNull();
  }

  @Test
  void classified_weekly_waste_keeps_exact_line_unknowns_and_real_ml_portion_value() {
    UUID raw = raw();
    ingest.record(
        new WastageInput(
            "CTB",
            92001,
            92002,
            date,
            "carlton draught pint",
            new BigDecimal("3"),
            new BigDecimal("4.13"),
            "KO",
            null,
            "ml",
            null,
            28,
            raw));
    ingest.record(
        new WastageInput(
            "CTB",
            92001,
            92003,
            date,
            "unmatched",
            BigDecimal.ONE,
            null,
            null,
            null,
            "each",
            null,
            null,
            raw));
    em.flush();
    mapping("CTB_RECIPE", "CTB", "28", date, "BEVERAGE", "UNKNOWN");
    var rows = weeklyInventory.wastage(date, date);
    assertThat(rows).hasSize(2);
    assertThat(
            rows.stream()
                .filter(r -> r.nature().equals("BEVERAGE"))
                .findFirst()
                .orElseThrow()
                .value())
        .isEqualByComparingTo("12.39");
    assertThat(rows.stream().mapToLong(r -> r.lines()).sum()).isEqualTo(2);
    assertThat(rows.stream().mapToLong(r -> r.valuedLines()).sum()).isEqualTo(1);
    mapping("WASTE_LINE", "CTB", "wastage:92001:92002", date, "UNKNOWN", "UNKNOWN");
    assertThat(weeklyInventory.wastage(date, date)).allMatch(r -> r.nature().equals("UNKNOWN"));
  }

  @Test
  void mappingPreviewReadsExactObservedWasteImpactWithoutAppendingARevision() {
    var raw = raw();
    ingest.record(
        new WastageInput(
            "CTB",
            93001,
            93002,
            date,
            "carlton draught pint",
            new BigDecimal("3"),
            new BigDecimal("4.13"),
            null,
            null,
            "ml",
            null,
            28,
            raw));
    em.flush();
    var owner =
        new com.goldys.platform.auth.UserRole(
            new com.goldys.platform.auth.DepartmentCode("ALL"),
            new com.goldys.platform.auth.SeniorityCode("OWNER"));
    var m =
        new com.goldys.platform.semantic.ProductReportingMapping(
            com.goldys.platform.semantic.ProductReportingMapping.Scope.WASTE_LINE,
            "CTB",
            "wastage:93001:93002",
            date,
            com.goldys.platform.semantic.ProductReportingMapping.SalesNature.BEVERAGE,
            com.goldys.platform.semantic.ProductReportingMapping.StockBucket.UNKNOWN,
            com.goldys.platform.semantic.ProductReportingMapping.ItemRole.UNKNOWN,
            com.goldys.platform.semantic.ProductReportingMapping.BusinessArea.UNKNOWN,
            com.goldys.platform.semantic.ProductReportingMapping.Lifecycle.UNKNOWN,
            "Reviewed exact source line");
    var result = mappingService.preview(owner, m, date.plusDays(6));
    assertThat(result.reviewable()).isTrue();
    assertThat(result.observedRows()).isEqualTo(1);
    assertThat(result.knownAmount()).isEqualByComparingTo("12.39");
    assertThat(result.amountBasis()).contains("tax basis unverified");
    assertThat(jdbc.queryForObject("select count(*) from product_reporting_mapping", Integer.class))
        .isZero();
  }

  @Test
  void source_evidence_queries_execute_on_the_current_postgres_schema() {
    assertThat(evidence.currentEvidence()).isNotNull();
  }

  @Test
  void polling_is_database_bounded_and_never_selects_snapshots_but_preview_still_does() {
    Instant now = Instant.parse("2026-11-03T00:00:00Z");
    ClassificationRun newest = null;
    for (int i = 0; i < 30; i++) {
      newest = ClassificationRun.create(ClassificationRunKind.ASSIGN, now.plusSeconds(i));
      newest.snapshot("{\"context\":\"retained\"}", "[{\"status\":\"NEEDS_REVIEW\"}]");
      newest.namingSnapshot("[{\"name\":\"Food\"}]");
      runs.save(newest);
    }
    em.flush();
    em.clear();
    var logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    var previousLevel = logger.getLevel();
    Thread testThread = Thread.currentThread();
    var statements =
        new ListAppender<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            if (Thread.currentThread() == testThread) super.append(event);
          }
        };
    statements.start();
    logger.addAppender(statements);
    logger.setLevel(Level.DEBUG);
    try {
      var listed = classification.listRuns();
      assertThat(listed).hasSize(25);
      assertThat(listed.getFirst().id()).isEqualTo(newest.id());
      assertThat(listed.getLast().startedAt()).isEqualTo(now.plusSeconds(5));
      assertThat(statements.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
          .singleElement()
          .satisfies(
              sql -> {
                assertThat(sql)
                    .contains("classification_run", "fetch first ? rows only")
                    .doesNotContain("context_snapshot", "proposal_snapshot", "naming_snapshot");
              });
      statements.list.clear();
      var preview = classification.preview(newest.id());
      assertThat(preview.contextSnapshot()).isEqualTo(newest.contextSnapshot());
      assertThat(preview.proposalSnapshot()).isEqualTo(newest.proposalSnapshot());
      assertThat(preview.namingSnapshot()).isEqualTo(newest.namingSnapshot());
      assertThat(statements.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
          .singleElement()
          .satisfies(
              sql ->
                  assertThat(sql)
                      .contains("context_snapshot", "proposal_snapshot", "naming_snapshot"));
    } finally {
      logger.detachAppender(statements);
      logger.setLevel(previousLevel);
      statements.stop();
    }
  }

  @Test
  void run_snapshots_survive_a_fresh_entity_read() {
    var run = ClassificationRun.create(ClassificationRunKind.ASSIGN, Instant.now());
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
