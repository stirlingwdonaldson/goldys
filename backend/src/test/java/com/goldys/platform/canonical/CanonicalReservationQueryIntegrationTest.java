package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalReservationQueryIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReservationService service;
  @Autowired CanonicalReservationQuery query;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_reservation");
  }

  @Test
  void derivesLocalTradingDateInVenueZone() {
    // 2026-09-20T05:00:00Z is 15:00 AEST (UTC+10) on 2026-09-20.
    service.record(
        new ReservationInput(
            "OPENTABLE",
            "r1",
            Instant.parse("2026-09-20T05:00:00Z"),
            4,
            "SEATED",
            "12",
            "OpenTable",
            "Smith",
            rawRecord("OPENTABLE")));
    // 2026-09-20T13:00:00Z is 23:00 AEST on 2026-09-20.
    service.record(
        new ReservationInput(
            "OPENTABLE",
            "r2",
            Instant.parse("2026-09-20T13:00:00Z"),
            2,
            "NO_SHOW",
            null,
            null,
            null,
            rawRecord("OPENTABLE")));

    List<ReservationView> views =
        query.currentReservationsForDates(Set.of(LocalDate.of(2026, 9, 20)));

    assertThat(views).hasSize(2);
    assertThat(views).allMatch(v -> v.tradingDate().equals(LocalDate.of(2026, 9, 20)));
    assertThat(views).extracting(ReservationView::status).containsExactlyInAnyOrder("SEATED", "NO_SHOW");
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
}
