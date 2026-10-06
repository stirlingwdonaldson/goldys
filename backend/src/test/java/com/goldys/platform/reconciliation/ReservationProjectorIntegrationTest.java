package com.goldys.platform.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.canonical.CanonicalReservationIngest;
import com.goldys.platform.canonical.ReservationInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class ReservationProjectorIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReservationIngest ingest;
  @Autowired ReservationProjector projector;
  @Autowired ResolvedReservationDayRepository resolved;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_reservation");
    jdbc.update("truncate table resolved_reservation_day");
    jdbc.update("truncate table reservation_override");
  }

  @Test
  void aggregatesCoversAndNoShowsPerServicePeriod() {
    // 15:00 AEST → DINNER, party 4 seated.
    recordReservation("r1", "2026-09-20T05:00:00Z", 4, "SEATED");
    // 12:00 AEST → LUNCH, party 2 no-show.
    recordReservation("r2", "2026-09-20T02:00:00Z", 2, "NO_SHOW");

    projector.recompute(LocalDate.of(2026, 9, 20));

    List<ResolvedReservationDay> rows = rowsFor(LocalDate.of(2026, 9, 20));
    assertThat(rows).hasSize(2);

    ResolvedReservationDay lunch = row(rows, "LUNCH");
    assertThat(lunch.bookings()).isEqualTo(1);
    assertThat(lunch.attended()).isZero();
    assertThat(lunch.covers()).isZero();
    assertThat(lunch.noShows()).isEqualTo(1);

    ResolvedReservationDay dinner = row(rows, "DINNER");
    assertThat(dinner.bookings()).isEqualTo(1);
    assertThat(dinner.attended()).isEqualTo(1);
    assertThat(dinner.covers()).isEqualTo(4);
    assertThat(dinner.noShows()).isZero();
  }

  @Test
  void unknownStatusCountsAsBookingOnly() {
    recordReservation("r1", "2026-09-20T05:00:00Z", 4, "SEATED");
    recordReservation("r2", "2026-09-20T06:00:00Z", 2, "UNKNOWN_STATUS");

    projector.recompute(LocalDate.of(2026, 9, 20));

    ResolvedReservationDay dinner = row(rowsFor(LocalDate.of(2026, 9, 20)), "DINNER");
    assertThat(dinner.bookings()).isEqualTo(2);
    assertThat(dinner.attended()).isEqualTo(1);
    assertThat(dinner.covers()).isEqualTo(4);
    assertThat(dinner.cancelled()).isZero();
    assertThat(dinner.noShows()).isZero();
    assertThat(dinner.walkIns()).isZero();
  }

  private List<ResolvedReservationDay> rowsFor(LocalDate date) {
    return resolved.findByTradingDateBetweenOrderByTradingDateAscServicePeriodAsc(date, date);
  }

  private static ResolvedReservationDay row(List<ResolvedReservationDay> rows, String period) {
    return rows.stream().filter(r -> r.servicePeriod().equals(period)).findFirst().orElseThrow();
  }

  private void recordReservation(String id, String at, int partySize, String status) {
    ingest.record(
        new ReservationInput(
            "OPENTABLE", id, Instant.parse(at), partySize, status, null, null, null, rawRecord()));
  }

  private UUID rawRecord() {
    UUID runId = UUID.randomUUID();
    UUID recordId = UUID.randomUUID();
    jdbc.update(
        "insert into ingestion_run (id, source_system, connector_name, status, started_at, fetched_count, persisted_count) "
            + "values (?, 'OPENTABLE', 'test', 'SUCCESS', now(), 1, 1)",
        runId);
    jdbc.update(
        "insert into raw_record (id, ingestion_run_id, source_system, fetch_method, content_type, payload_bytes, payload_sha256, payload_byte_length, fetcher_identity, fetched_at) "
            + "values (?, ?, 'OPENTABLE', 'FILE_EXPORT', 'text/csv', ?, ?, ?, 'test', now())",
        recordId,
        runId,
        new byte[] {1},
        "0".repeat(64),
        1);
    return recordId;
  }
}
