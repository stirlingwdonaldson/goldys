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

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesProjectorIntegrationTest {

  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
  private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesIngest ingest;
  @Autowired ProductSalesProjector projector;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void conflictingPairProducesAnException() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);

    var rows = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).entityKey()).isEqualTo("garlic aioli");
    assertThat(rows.get(0).status()).isEqualTo("conflict");
  }

  @Test
  void sameProductOnTwoDatesProducesTwoExceptions() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    ingest.record(input("LIGHTSPEED", SEP_15, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_15, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);
    projector.recompute("garlic aioli", SEP_15);

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(2);
  }

  @Test
  void agreeingPairProducesNoException() {
    ingest.record(input("LIGHTSPEED", SEP_14, "chips", "10", "50.00"));
    ingest.record(input("CTB", SEP_14, "chips", "10", "50.00"));
    projector.recompute("chips", SEP_14);

    assertThat(exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales")).isEmpty();
  }

  @Test
  void recomputeAllIsIdempotent() {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recomputeAll();
    var first = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");

    projector.recomputeAll();
    var second = exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales");

    assertThat(second).hasSize(first.size());
    assertThat(second)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("detectedAt")
        .containsExactlyInAnyOrderElementsOf(first);
  }

  @Test
  void detectedAtSurvivesRecomputeWhileOpen() throws Exception {
    ingest.record(input("LIGHTSPEED", SEP_14, "garlic aioli", "150", "380.88"));
    ingest.record(input("CTB", SEP_14, "garlic aioli", "127", "322.46"));
    projector.recompute("garlic aioli", SEP_14);
    var first =
        exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales").get(0).detectedAt();

    Thread.sleep(10);
    projector.recompute("garlic aioli", SEP_14);
    var second =
        exceptions.findByEntityTypeOrderByTradingDateDesc("product_sales").get(0).detectedAt();

    assertThat(second).isEqualTo(first);
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
