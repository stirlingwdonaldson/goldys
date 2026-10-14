package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises source-scoped product identity and the reviewable source→hub mapping: a rename versions
 * the name (not the identity), multiple source ids map to one hub, cross-source ids stay distinct,
 * a correction supersedes the prior mapping, and a one-to-many hub never duplicates a source
 * product.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductIdentityIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ProductMappingIngest productMapping;
  @Autowired SourceProductSaleFactIngest sourceSaleFact;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table source_product_sale_fact, product_mapping, stock_item, source_product, product");
  }

  @Test
  void renameVersionsTheNameNotTheIdentity() {
    UUID raw = rawRecord();
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer", raw));
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer Premium", raw));

    // One current source product, two versions; the logical id is stable across the rename.
    assertThat(count("select count(*) from source_product where source_record_ref='product:1'"))
        .isEqualTo(2);
    assertThat(
            count(
                "select count(*) from source_product where source_record_ref='product:1' "
                    + "and superseded_at is null"))
        .isEqualTo(1);
  }

  @Test
  void multipleSourceIdsMapToOneHub() {
    UUID raw = rawRecord();
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer", raw));
    productMapping.mapSourceProduct(source("CTB", "STK-1", "Beer", raw));

    // One hub ("beer"), two source products, two mappings — never merged into one source identity.
    assertThat(count("select count(*) from product")).isEqualTo(1);
    assertThat(count("select count(*) from source_product")).isEqualTo(2);
    assertThat(count("select count(*) from product_mapping where superseded_at is null"))
        .isEqualTo(2);
  }

  @Test
  void sameCodeAcrossSourcesIsDistinct() {
    UUID raw = rawRecord();
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer", raw));
    productMapping.mapSourceProduct(source("CTB", "product:1", "Wine", raw));

    // Same code text, different source system: two distinct source products.
    assertThat(count("select count(*) from source_product")).isEqualTo(2);
  }

  @Test
  void correctionSupersedesThePriorMapping() {
    UUID raw = rawRecord();
    UUID hub = productMapping.mapSourceProduct(source("CTB", "STK-1", "Beer", raw));

    // A human re-maps the source product to a different hub (a manual correction).
    UUID otherHub = jdbc.queryForObject("select gen_random_uuid()", UUID.class);
    jdbc.update("insert into product (id, name, created_at) values (?, 'other', now())", otherHub);
    productMapping.map(
        new ProductMappingInput("CTB", "STK-1", otherHub, "MANUAL", "ACCEPTED", raw));

    assertThat(count("select count(*) from product_mapping where source_record_ref='STK-1'"))
        .isEqualTo(2); // auto mapping superseded by the manual correction
    assertThat(
            count(
                "select count(*) from product_mapping where source_record_ref='STK-1' "
                    + "and superseded_at is null and product_id = ?",
                otherHub))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from product_mapping where source_record_ref='STK-1' "
                    + "and superseded_at is null and product_id = ?",
                hub))
        .isZero();
  }

  @Test
  void oneToManyHubDoesNotDuplicateSourceProducts() {
    UUID raw = rawRecord();
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer", raw));
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:2", "Beer", raw));
    productMapping.mapSourceProduct(source("CTB", "STK-1", "Beer", raw));

    // Three distinct source products, one hub — each source product appears exactly once.
    assertThat(count("select count(*) from source_product")).isEqualTo(3);
    assertThat(count("select count(*) from product")).isEqualTo(1);
    assertThat(
            count(
                "select count(distinct source_system || ':' || source_record_ref) "
                    + "from product_mapping where superseded_at is null"))
        .isEqualTo(3);
  }

  @Test
  void crossSourceNameMatchIsFlaggedForReview() {
    UUID raw = rawRecord();
    productMapping.mapSourceProduct(source("LIGHTSPEED", "product:1", "Beer", raw));
    productMapping.mapSourceProduct(source("CTB", "STK-1", "Beer", raw));

    // The same-name match from the second source is recorded as a NAME-confidence mapping, so a
    // human reviews the cross-source assumption rather than it being silently asserted.
    assertThat(
            jdbc.queryForObject(
                "select confidence from product_mapping "
                    + "where source_system='CTB' and source_record_ref='STK-1' and superseded_at is null",
                String.class))
        .isEqualTo("NAME");
    assertThat(
            jdbc.queryForObject(
                "select confidence from product_mapping "
                    + "where source_system='LIGHTSPEED' and source_record_ref='product:1' and superseded_at is null",
                String.class))
        .isEqualTo("SOURCE_CODE");
  }

  @Test
  void sourceNativeMeasuresArePreserved() {
    UUID raw = rawRecord();
    sourceSaleFact.record(
        new SourceProductSaleFactInput(
            "CTB",
            "STK-1@2026-09-14",
            java.time.LocalDate.of(2026, 9, 14),
            "STK-1",
            new java.math.BigDecimal("12.50"),
            new java.math.BigDecimal("250.00"),
            new java.math.BigDecimal("10.00"),
            new java.math.BigDecimal("22.73"),
            new java.math.BigDecimal("13.75"),
            raw));

    assertThat(
            jdbc.queryForObject(
                "select unit_price || '|' || cost_per_unit || '|' || total_food_cost "
                    + "from source_product_sale_fact where source_record_ref='STK-1@2026-09-14'",
                String.class))
        .isEqualTo("13.7500|12.5000|250.0000");
  }

  private static SourceProductInput source(String sourceSystem, String ref, String name, UUID raw) {
    return new SourceProductInput(sourceSystem, ref, null, name, null, null, raw);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
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
