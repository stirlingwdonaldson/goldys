package com.goldys.platform.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end coverage of the canonical review write path against real PostgreSQL 16: supersession
 * of a corrected fact, and the guest link populated from a source {@code Guest ID}. Loading the
 * Spring context applies {@code V65} via Flyway and validates the entity against it ({@code
 * ddl-auto: validate}); a failure in either fails every test here.
 */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class CanonicalReviewIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReviewService service;
  @Autowired CanonicalReviewRepository repository;
  @Autowired CanonicalGuestService guestService;
  @Autowired CanonicalGuestRepository guestRepository;

  @BeforeEach
  void clean() {
    jdbc.update("truncate table canonical_review, canonical_guest");
  }

  @Test
  void recordsAndSupersedesReview() {
    UUID rawId = rawRecord("OPENTABLE");

    CanonicalReview first = service.record(input("RT-7001", 4, null, null, rawId));
    CanonicalReview successor = service.record(input("RT-7001", 5, null, null, rawId));

    assertThat(successor.id()).isNotEqualTo(first.id());
    assertThat(successor.logicalEntityId()).isEqualTo(first.logicalEntityId());
    assertThat(repository.findById(first.id()).orElseThrow().supersededAt()).isNotNull();
    assertThat(repository.findAllCurrent())
        .filteredOn(r -> r.sourceRecordRef().equals("RT-7001"))
        .hasSize(1)
        .extracting(CanonicalReview::overallRating)
        .containsExactly(5);
  }

  @Test
  void populatesGuestAndLinks() {
    UUID rawId = rawRecord("OPENTABLE");
    String guestId = "guest-4242";
    String guestName = "Ada Lovelace";

    guestService.ensure(guestId, guestName, rawId);
    service.record(input("RT-7002", 5, guestId, guestName, rawId));

    CanonicalGuest guest = guestRepository.findCurrentByRef(guestId).get(0);
    assertThat(guest.guestName()).isEqualTo(guestName);

    CanonicalReview review =
        repository.findAllCurrent().stream()
            .filter(r -> r.sourceRecordRef().equals("RT-7002"))
            .findFirst()
            .orElseThrow();
    assertThat(review.guestId()).isEqualTo(guest.logicalEntityId());
  }

  private ReviewInput input(
      String reviewId, Integer overallRating, String guestId, String guestName, UUID rawId) {
    return new ReviewInput(
        "OPENTABLE",
        reviewId,
        "OpenTable",
        Instant.parse("2026-09-20T10:00:00Z"),
        Instant.parse("2026-09-19T19:00:00Z"),
        overallRating,
        5,
        4,
        4,
        5,
        "Quiet",
        true,
        "Great food",
        "Regular",
        List.of("Great food", "Friendly"),
        List.of("steak", "fries"),
        guestId,
        guestName,
        rawId);
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
