package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalDailySalesIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalDailySalesService service;
  @Autowired CanonicalDailySalesRepository repository;

  @Test
  void twoSourcesForTheSameDateShareTheLogicalId() {
    LocalDate date = LocalDate.of(2026, 9, 13);

    CanonicalDailySales lightspeed =
        service.record(
            new DailySalesInput(
                "LIGHTSPEED",
                date,
                bd("27650.66"),
                bd("2502.36"),
                bd("25148.30"),
                rawRecord("LIGHTSPEED")));
    CanonicalDailySales ctb =
        service.record(
            new DailySalesInput(
                "CTB", date, bd("20990.83"), bd("1907.06"), bd("19083.77"), rawRecord("CTB")));

    assertThat(lightspeed.logicalEntityId()).isEqualTo(ctb.logicalEntityId());
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    LocalDate date = LocalDate.of(2026, 9, 14);
    UUID raw = rawRecord("LIGHTSPEED");
    DailySalesInput input =
        new DailySalesInput("LIGHTSPEED", date, bd("9520.22"), bd("865.71"), bd("8654.51"), raw);

    CanonicalDailySales first = service.record(input);
    CanonicalDailySales retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(repository.findCurrentByDate(date)).hasSize(1);
  }

  @Test
  void correctionSupersedesThePriorVersion() {
    LocalDate date = LocalDate.of(2026, 9, 15);
    UUID raw = rawRecord("CTB");
    CanonicalDailySales first =
        service.record(
            new DailySalesInput("CTB", date, bd("11339.96"), bd("1030.40"), bd("10309.56"), raw));
    CanonicalDailySales corrected =
        service.record(
            new DailySalesInput("CTB", date, bd("11400.00"), bd("1035.00"), bd("10365.00"), raw));

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findCurrentByDate(date)).hasSize(1);
  }

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

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }
}
