package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

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
class CanonicalProductSalesIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalProductSalesService service;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_product_sales");
  }

  @Test
  void twoSourcesForTheSameProductAndDateShareTheLogicalId() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    var ls =
        service.record(
            new ProductSalesInput(
                "LIGHTSPEED",
                date,
                "pint carlton draught",
                bd("1232"),
                bd("17340.98"),
                rawRecord()));
    var ctb =
        service.record(
            new ProductSalesInput(
                "CTB", date, "pint carlton draught", bd("1232"), bd("17340.98"), rawRecord()));

    assertThat(ls.logicalEntityId()).isEqualTo(ctb.logicalEntityId());
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    ProductSalesInput input =
        new ProductSalesInput("CTB", date, "chicken parma", bd("391"), bd("11418.06"), rawRecord());

    var first = service.record(input);
    var retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
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
