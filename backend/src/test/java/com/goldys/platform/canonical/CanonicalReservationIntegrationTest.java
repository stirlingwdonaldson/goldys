package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalReservationIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReservationService service;
  @Autowired CanonicalReservationRepository repository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_reservation");
  }

  @Test
  void unchangedRetryDoesNotAppendAVersion() {
    ReservationInput input = input("OPENTABLE", "1000000001", "2026-09-23T09:30:00Z");

    CanonicalReservation first = service.record(input);
    CanonicalReservation retry = service.record(input);

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(repository.findCurrentByRef("1000000001")).hasSize(1);
  }

  @Test
  void changedFactSupersedesThePriorVersion() {
    ReservationInput before = input("OPENTABLE", "1000000002", "2026-09-23T09:30:00Z");
    CanonicalReservation first = service.record(before);
    ReservationInput after =
        new ReservationInput(
            "OPENTABLE",
            "1000000002",
            Instant.parse("2026-09-23T09:30:00Z"),
            5,
            "CANCELLED",
            null,
            "OpenTable",
            "Jones",
            before.rawRecordId());

    CanonicalReservation corrected = service.record(after);

    assertThat(corrected.id()).isNotEqualTo(first.id());
    assertThat(corrected.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findCurrentByRef("1000000002")).hasSize(1);
    assertThat(repository.findCurrentByRef("1000000002").get(0).status()).isEqualTo("CANCELLED");
  }

  @Test
  void differentSourcesKeepSeparateCurrentRows() {
    service.record(input("OPENTABLE", "1000000003", "2026-09-23T09:30:00Z"));
    service.record(input("MANUAL", "1000000003", "2026-09-23T09:30:00Z"));

    assertThat(repository.findCurrentByRef("1000000003")).hasSize(2);
  }

  private ReservationInput input(String source, String id, String at) {
    return new ReservationInput(
        source, id, Instant.parse(at), 4, "BOOKED", "12", "OpenTable", "Smith", rawRecord(source));
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
