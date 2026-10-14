package com.goldys.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.CanonicalReviewIngest;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the classify-on-ingest path persists the {@code review_classification} row through an
 * independently committed transaction: the {@code AFTER_COMMIT} listener runs with the writer's
 * transaction synchronization still bound, so a plain repository {@code save()} would join the
 * already-committed transaction and be lost. Recording a canonical review must leave the
 * classification row visible from a fresh transaction (a new JDBC connection).
 */
@SpringBootTest(properties = "classification.jev.enabled=true")
@Import(PostgresContainerConfiguration.class)
class ReviewClassificationServiceIntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired CanonicalReviewIngest reviewIngest;
  @Autowired ReviewClassificationRepository repository;

  @MockitoBean ReviewClassifier classifier;

  @BeforeEach
  void clean() {
    jdbc.update(
        "truncate table review_classification, review_classification_run, canonical_review");
  }

  @Test
  void classificationRowIsVisibleFromFreshTransactionAfterCanonicalRecord() {
    when(classifier.classify(any()))
        .thenReturn(
            new ReviewClassification(
                new Sentiment("POSITIVE", new BigDecimal("0.90")),
                new BigDecimal("4.5"),
                List.of(new AspectSentiment("service", "POSITIVE", new BigDecimal("0.95")))));

    UUID rawId = rawRecord("OPENTABLE");
    ReviewInput input =
        new ReviewInput(
            "OPENTABLE",
            "OT-IT-1",
            "OpenTable",
            Instant.parse("2026-10-12T10:00:00Z"),
            Instant.parse("2026-10-11T19:00:00Z"),
            4,
            5,
            4,
            4,
            5,
            "Quiet",
            true,
            "Great food and service",
            null,
            List.of("Friendly"),
            List.of("steak"),
            null,
            "Ada Lovelace",
            rawId);

    // Records the canonical fact transactionally and fires the AFTER_COMMIT classification listener
    // on the same thread, which persists the classification in its own REQUIRES_NEW transaction.
    reviewIngest.record(input);

    // A fresh JDBC connection (a new transaction) must already see the committed row.
    Integer count =
        jdbc.queryForObject(
            "select count(*) from review_classification where review_id = ?",
            Integer.class,
            "OT-IT-1");
    assertThat(count).isEqualTo(1);

    ReviewClassificationEntity row = repository.findById("OT-IT-1").orElseThrow();
    assertThat(row.sentiment()).isEqualTo("POSITIVE");
    assertThat(row.sentimentConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
    assertThat(row.predictedRating()).isEqualByComparingTo(new BigDecimal("4.5"));
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
