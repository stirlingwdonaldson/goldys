package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
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
class DailySalesProjectorIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired DailySalesProjector projector;
  @Autowired ResolvedDailySalesRepository resolved;
  @Autowired ReconciliationExceptionRowRepository exceptions;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table canonical_daily_sales");
  }

  @Test
  void agreeingSourcesResolveWithNoException() {
    ingest.record(input("LIGHTSPEED", SEP_13, "9694.80"));
    ingest.record(input("CTB", SEP_13, "9694.80"));
    projector.recompute(SEP_13);

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.resolutionType()).isEqualTo("agreed");
    assertThat(row.totalSales()).isEqualByComparingTo("9694.80");
    assertThat(row.hasConflict()).isFalse();
    assertThat(exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales")).isEmpty();
  }

  @Test
  void conflictingSourcesProduceAnException() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recompute(SEP_13);

    ResolvedDailySales row = resolved.findTopByOrderByTradingDateDesc().get();
    assertThat(row.hasConflict()).isTrue();
    assertThat(row.resolutionType()).isEqualTo("conflict");
    assertThat(row.totalSales()).isNull();

    var rows = exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).status()).isEqualTo("conflict");
    assertThat(rows.get(0).tradingDate()).isEqualTo(SEP_13);
  }

  @Test
  void recomputeAllIsIdempotent() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recomputeAll();
    var first = resolved.findAll();

    projector.recomputeAll();
    var second = resolved.findAll();

    assertThat(second).hasSize(first.size());
    // Compare by field values, not entity identity: recompute builds fresh ResolvedDailySales
    // instances and refreshed resolvedAt timestamps each pass, so neither default equality nor a
    // full recursive comparison applies. Idempotency means the same trading dates and resolutions.
    assertThat(second)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("resolvedAt")
        .containsExactlyInAnyOrderElementsOf(first);
  }

  @Test
  void detectedAtSurvivesRecomputeWhileOpen() throws Exception {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    projector.recompute(SEP_13);
    var first = exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales").get(0).detectedAt();

    Thread.sleep(5);
    projector.recompute(SEP_13);
    var second =
        exceptions.findByEntityTypeOrderByTradingDateAsc("daily_sales").get(0).detectedAt();

    assertThat(second).isEqualTo(first);
  }

  private DailySalesInput input(String source, LocalDate date, String total) {
    return new DailySalesInput(
        source,
        date,
        new BigDecimal(total),
        new BigDecimal("0"),
        new BigDecimal("0"),
        rawRecord(source));
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id), so each canonical
  // row needs a real ingestion_run + raw_record row (mirrors CanonicalDailySalesIntegrationTest).
  private UUID rawRecord(String source) {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
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
    return recordId;
  }
}
