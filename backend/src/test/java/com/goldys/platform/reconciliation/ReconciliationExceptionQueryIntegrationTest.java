package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.DailySalesInput;
import com.goldys.platform.reconciliation.ReconciliationExceptionQuery.DailyException;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
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
class ReconciliationExceptionQueryIntegrationTest {

  private static final LocalDate SEP_13 = LocalDate.of(2026, 9, 13);
  private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesIngest ingest;
  @Autowired ReconciliationExceptionRowRepository exceptions;
  @Autowired ReconciliationExceptionQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table resolved_daily_sales");
    jdbc.update("truncate table canonical_daily_sales");
  }

  @Test
  void listDailyJoinsPerSourceCanonicalValues() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    resetProjections();
    exceptions.save(
        new ReconciliationExceptionRow(
            "daily_sales", SEP_13.toString(), "daily_sales", SEP_13, "conflict", Instant.EPOCH));

    var result = query.listDaily();

    assertThat(result).hasSize(1);
    var ex = result.get(0);
    assertThat(ex.tradingDate()).isEqualTo(SEP_13);
    assertThat(ex.status()).isEqualTo("conflict");
    assertThat(ex.sources())
        .extracting(SourceTotal::sourceSystem)
        .containsExactlyInAnyOrder("LIGHTSPEED", "CTB");
    assertThat(ex.sources())
        .extracting(SourceTotal::totalSales)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactlyInAnyOrder(new BigDecimal("27650.66"), new BigDecimal("20990.83"));
  }

  @Test
  void listDailyReturnsEmptyWhenThereAreNoExceptions() {
    assertThat(query.listDaily()).isEmpty();
  }

  @Test
  void listDailyReturnsNewestFirst() {
    ingest.record(input("LIGHTSPEED", SEP_13, "27650.66"));
    ingest.record(input("CTB", SEP_13, "20990.83"));
    ingest.record(input("LIGHTSPEED", SEP_14, "100.00"));
    ingest.record(input("CTB", SEP_14, "100.00"));
    resetProjections();
    exceptions.save(
        new ReconciliationExceptionRow(
            "daily_sales", SEP_13.toString(), "daily_sales", SEP_13, "conflict", Instant.EPOCH));
    exceptions.save(
        new ReconciliationExceptionRow(
            "daily_sales", SEP_14.toString(), "daily_sales", SEP_14, "conflict", Instant.EPOCH));

    var result = query.listDaily();

    assertThat(result).hasSize(2);
    assertThat(result).extracting(DailyException::tradingDate).containsExactly(SEP_14, SEP_13);
  }

  // ingest fires the projector synchronously; reset the projections so this test controls its own
  // exception rows deterministically (canonical rows are untouched).
  private void resetProjections() {
    jdbc.update("truncate table reconciliation_exception");
    jdbc.update("truncate table resolved_daily_sales");
  }

  private DailySalesInput input(String source, LocalDate date, String total) {
    return new DailySalesInput(
        source, date, new BigDecimal(total), new BigDecimal("0"), new BigDecimal("0"),
        rawRecord(source));
  }

  // canonical_daily_sales.raw_record_id is a NOT NULL FK to raw_record(id), so each canonical
  // row needs a real ingestion_run + raw_record row (mirrors DailySalesProjectorIntegrationTest).
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
