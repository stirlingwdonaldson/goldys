package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ProductSalesSeederIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ReconciliationExceptionRowRepository repository;
  @Autowired StartupProjectionSeeder seeder;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table reconciliation_exception, canonical_product_sales, product_sales_override, resolution_rule");
  }

  @Test
  void seedsProductExceptionsFromExistingCanonicalData() {
    seedCanonical("LIGHTSPEED", LocalDate.of(2026, 9, 14), "garlic aioli", "150", "380.88");
    seedCanonical("CTB", LocalDate.of(2026, 9, 14), "garlic aioli", "127", "322.46");

    seeder.run(mockApplicationArguments());

    assertThat(repository.findByEntityTypeOrderByTradingDateDesc("product_sales")).hasSize(1);
  }

  private void seedCanonical(String source, LocalDate date, String key, String qty, String amount) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    UUID logicalId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, ?, 'test', 'SUCCESS', now(), 1, 1)",
        runId,
        source);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, ?, 'API', 'application/json', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    jdbc.update(
        "insert into canonical_product_sales (id, logical_entity_id, trading_date, product_name_key, source_system, source_record_ref, raw_record_id, quantity_sold, amount, valid_from, recorded_at) "
            + "values (?, ?, ?, ?, ?, 'ref', ?, ?, ?, now(), now())",
        entityId,
        logicalId,
        date,
        key,
        source,
        recordId,
        new BigDecimal(qty),
        new BigDecimal(amount));
  }

  private static ApplicationArguments mockApplicationArguments() {
    return org.mockito.Mockito.mock(ApplicationArguments.class);
  }
}
