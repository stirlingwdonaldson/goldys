package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.reconciliation.DailySalesConflict;
import com.goldys.platform.reconciliation.DailySalesReconciliationService;
import com.goldys.platform.reconciliation.DailySalesResolved;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end acceptance for the daily-sales slice: canonicalize two sources, then reconcile and
 * resolve. Includes the known 13 Sep gap.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class TradingWeekAcceptanceTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesService dailySales;
  @Autowired DailySalesReconciliationService reconciliation;

  @Test
  void surfacesTheKnownConflictAndResolvesToUnresolved() {
    // Lightspeed ~$6.7k higher than CTB on 13 Sep; they agree on 14 Sep.
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_13, bd("27650.66"), bd("2502.36"), bd("25148.30"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "CTB", SEP_13, bd("20990.83"), bd("1907.06"), bd("19083.77"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "LIGHTSPEED", SEP_14, bd("9694.80"), bd("880.91"), bd("8813.89"), rawRecord()));
    dailySales.record(
        new DailySalesInput(
            "CTB", SEP_14, bd("9694.80"), bd("880.91"), bd("8813.89"), rawRecord()));

    List<DailySalesConflict> conflicts = reconciliation.conflicts();

    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).tradingDate()).isEqualTo(SEP_13);
    assertThat(conflicts.get(0).status()).isEqualTo("conflict");

    Optional<DailySalesResolved> resolved = reconciliation.resolved(SEP_13);
    assertThat(resolved).isPresent();
    assertThat(resolved.get().resolvedTotal()).isNull(); // unresolved conflict
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
