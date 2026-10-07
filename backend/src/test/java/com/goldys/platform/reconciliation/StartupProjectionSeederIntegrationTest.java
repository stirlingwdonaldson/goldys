package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
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
class StartupProjectionSeederIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired ResolvedDailySalesRepository repository;
  @Autowired StartupProjectionSeeder seeder;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
  }

  @Test
  void seedsWhenProjectionIsEmpty() {
    // Seed canonical directly via SQL so the projection-listener does not run and pre-populate the
    // projection — the point is to exercise the seeder's own recomputeAll().
    seedCanonical("CTB", LocalDate.of(2026, 9, 13), "100.00");

    seeder.run(mockApplicationArguments());

    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findTopByOrderByTradingDateDesc().get().resolutionType())
        .isEqualTo("missing");
  }

  @Test
  void doesNotRecomputeWhenProjectionIsAlreadyPopulated() {
    repository.save(
        new ResolvedDailySales(
            LocalDate.of(2026, 9, 13),
            new BigDecimal("100.00"),
            null,
            null,
            "agreed",
            "agreed",
            false,
            Instant.EPOCH));
    jdbc.update("truncate table canonical_daily_sales");

    seeder.run(mockApplicationArguments());

    // Projection untouched because it was not empty.
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findTopByOrderByTradingDateDesc().get().resolutionType())
        .isEqualTo("agreed");
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id) — insert real rows.
  private void seedCanonical(String source, LocalDate date, String total) {
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
            + "values (?, ?, ?, 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        source,
        new byte[] {1},
        "0".repeat(64),
        1);
    jdbc.update(
        "insert into canonical_daily_sales (id, logical_entity_id, trading_date, source_system, source_record_ref, raw_record_id, total_sales, gst_total, net_total, valid_from, recorded_at) "
            + "values (?, ?, ?, ?, 'ref', ?, ?, 0, 0, now(), now())",
        entityId,
        logicalId,
        date,
        source,
        recordId,
        new BigDecimal(total));
  }

  private static ApplicationArguments mockApplicationArguments() {
    return org.mockito.Mockito.mock(ApplicationArguments.class);
  }
}
