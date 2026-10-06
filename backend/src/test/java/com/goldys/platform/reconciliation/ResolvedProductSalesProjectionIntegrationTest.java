package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import com.goldys.platform.canonical.ProductSalesInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves product reporting derives from the resolved projection, so multiple source observations of
 * the same business fact never double-count.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ResolvedProductSalesProjectionIntegrationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest ingest;
  @Autowired ProductSalesProjector projector;
  @Autowired ResolvedProductSalesRepository resolved;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table reconciliation_exception, resolved_product_sales, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void agreeingSourcesResolveToOneRowNotTheirSum() {
    ingest.record(input("LIGHTSPEED", SEP_14, "chips", "10", "50.00"));
    ingest.record(input("CTB", SEP_14, "chips", "10", "50.00"));
    projector.recompute("chips", SEP_14);

    ResolvedProductSales row = resolved.findByTradingDateAndProductNameKey(SEP_14, "chips").get();
    assertThat(row.resolutionType()).isEqualTo("agreed");
    assertThat(row.quantitySold()).isEqualByComparingTo("10");
    assertThat(row.amount()).isEqualByComparingTo("50.00");
    assertThat(row.hasConflict()).isFalse();
  }

  @Test
  void singleSourceIsTrustedAndResolved() {
    ingest.record(input("LIGHTSPEED", SEP_14, "chips", "10", "50.00"));
    projector.recompute("chips", SEP_14);

    ResolvedProductSales row = resolved.findByTradingDateAndProductNameKey(SEP_14, "chips").get();
    assertThat(row.resolutionType()).isEqualTo("single");
    assertThat(row.authoritativeSource()).isEqualTo("LIGHTSPEED");
    assertThat(row.quantitySold()).isEqualByComparingTo("10");
    assertThat(row.amount()).isEqualByComparingTo("50.00");
    assertThat(row.hasConflict()).isFalse();
  }

  @Test
  void disagreeingSourcesRemainUnresolvedWithNullValues() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);

    ResolvedProductSales row =
        resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get();
    assertThat(row.resolutionType()).isEqualTo("conflict");
    assertThat(row.quantitySold()).isNull();
    assertThat(row.amount()).isNull();
    assertThat(row.hasConflict()).isTrue();
  }

  @Test
  void manualOverrideSelectsOneSource() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    jdbc.update(
        "insert into product_sales_override (id, trading_date, product_name_key, authoritative_source, reason, actor_email, recorded_at) "
            + "values (?, ?, ?, 'LIGHTSPEED', 'trust lightspeed', 'a@b.com', now())",
        UUID.randomUUID(),
        SEP_14,
        "garlic aioli");
    projector.recompute("garlic aioli", SEP_14);

    ResolvedProductSales row =
        resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get();
    assertThat(row.resolutionType()).isEqualTo("override");
    assertThat(row.authoritativeSource()).isEqualTo("LIGHTSPEED");
    assertThat(row.quantitySold()).isEqualByComparingTo("150");
    assertThat(row.amount()).isEqualByComparingTo("380.88");
    assertThat(row.hasConflict()).isFalse();
  }

  @Test
  void priorityRuleSelectsOneSource() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    jdbc.update(
        "insert into resolution_rule (id, entity_type, field_key, strategy, source_priority, actor_email, recorded_at) "
            + "values (?, 'product_sales', 'garlic aioli', 'priority', ?::jsonb, 'a@b.com', now())",
        UUID.randomUUID(),
        "[\"CTB\"]");
    projector.recompute("garlic aioli", SEP_14);

    ResolvedProductSales row =
        resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get();
    assertThat(row.resolutionType()).isEqualTo("rule");
    assertThat(row.authoritativeSource()).isEqualTo("CTB");
    assertThat(row.quantitySold()).isEqualByComparingTo("127");
    assertThat(row.amount()).isEqualByComparingTo("322.46");
    assertThat(row.hasConflict()).isFalse();
  }

  @Test
  void ruleChangeRecomputesTheAffectedPair() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);

    // No rule yet: unresolved.
    assertThat(
            resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get().hasConflict())
        .isTrue();

    // Add a priority rule and recompute: now resolved to CTB.
    jdbc.update(
        "insert into resolution_rule (id, entity_type, field_key, strategy, source_priority, actor_email, recorded_at) "
            + "values (?, 'product_sales', 'garlic aioli', 'priority', ?::jsonb, 'a@b.com', now())",
        UUID.randomUUID(),
        "[\"CTB\"]");
    projector.recompute("garlic aioli", SEP_14);

    ResolvedProductSales row =
        resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get();
    assertThat(row.hasConflict()).isFalse();
    assertThat(row.authoritativeSource()).isEqualTo("CTB");
    assertThat(row.amount()).isEqualByComparingTo("322.46");
  }

  @Test
  void recomputeAllBuildsResolvedRowsForEveryProductDay() {
    ingest.record(input("LIGHTSPEED", SEP_14, "chips", "10", "50.00"));
    ingest.record(input("CTB", SEP_14, "chips", "10", "50.00"));
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recomputeAll();

    assertThat(resolved.findAll()).hasSize(2);
    assertThat(resolved.findByTradingDateAndProductNameKey(SEP_14, "chips").get().hasConflict())
        .isFalse();
    assertThat(
            resolved.findByTradingDateAndProductNameKey(SEP_14, "garlic aioli").get().hasConflict())
        .isTrue();
  }

  private ProductSalesInput input(
      String source, LocalDate date, String key, String qty, String amount) {
    return new ProductSalesInput(source, date, key, bd(qty), bd(amount), rawRecord());
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
            + "values (?, ?, 'CTB', 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
