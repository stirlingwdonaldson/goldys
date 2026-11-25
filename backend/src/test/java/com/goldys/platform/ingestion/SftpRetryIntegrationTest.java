package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class SftpRetryIntegrationTest {
  @Autowired IngestionService ingestion;
  @Autowired JdbcTemplate jdbc;
  @Autowired IngestionRunService runs;

  @Test
  void cooldownUsesRetainedBytesAndASeparateSuccessCannotHideAnOutstandingFile() {
    String ref = "sftp:" + UUID.randomUUID() + ".csv";
    byte[] bytes = {1, 2, 3};
    UUID raw = capture(ref, bytes);
    ingestion.recordQualification(raw, "INVOICE_IDENTITY_WITHHELD", "Identity requires review");
    ingestion.recordStage(raw, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
    UUID unrelated = capture("sftp:" + UUID.randomUUID() + ".csv", bytes);
    ingestion.recordStage(
        unrelated, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.SUCCESS);
    assertThat(ingestion.deferSftpRetry(ref, bytes)).isTrue();
    assertThat(ingestion.deferSftpRetry(ref, new byte[] {4})).isFalse();
    assertThat(ingestion.connectorHealthRuns())
        .anySatisfy(
            r -> {
              assertThat(r.connectorName()).isEqualTo("ctb-invoices");
              assertThat(r.status()).isEqualTo("PARTIAL");
              assertThat(r.failure().type()).isEqualTo("INVOICE_IDENTITY_WITHHELD");
            });
    assertThat(ingestion.outstandingSftpRuns())
        .anySatisfy(
            r -> {
              assertThat(r.failure().type()).isEqualTo("INVOICE_IDENTITY_WITHHELD");
              assertThat(r.failure().message()).isEqualTo("Identity requires review");
            });
    UUID run =
        jdbc.queryForObject("select ingestion_run_id from raw_record where id=?", UUID.class, raw);
    assertThat(
            jdbc.queryForObject(
                "select source_record_ref from raw_record where id=?", String.class, raw))
        .isEqualTo(ref);
    UUID recovery = capture(ref, bytes);
    // Capturing bytes again is not proof of recovery.
    assertThat(ingestion.outstandingSftpRuns())
        .anySatisfy(r -> assertThat(r.failure().message()).isEqualTo("Identity requires review"));
    ingestion.recordStage(
        recovery, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.SUCCESS);
    assertThat(ingestion.deferSftpRetry(ref, bytes)).isFalse();
    assertThat(
            jdbc.queryForList(
                "select id from ingestion_run where id=? and status='PARTIAL'", UUID.class, run))
        .containsExactly(run);
    assertThat(ingestion.outstandingSftpRuns())
        .noneSatisfy(r -> assertThat(r.failure().message()).isEqualTo("Identity requires review"));
  }

  @Test
  void rawCaptureIsNotProcessingSuccessAndInterruptedDeliveriesAreRecoverable() {
    String ref = "sftp:" + UUID.randomUUID() + ".csv";
    UUID raw = capture(ref, new byte[] {9});
    UUID run =
        jdbc.queryForObject("select ingestion_run_id from raw_record where id=?", UUID.class, raw);
    assertThat(
            jdbc.queryForObject("select status from ingestion_run where id=?", String.class, run))
        .isEqualTo("RUNNING");
    runs.recoverDanglingRuns(java.time.Instant.now());
    assertThat(
            jdbc.queryForObject("select status from ingestion_run where id=?", String.class, run))
        .isEqualTo("PARTIAL");
    assertThat(ingestion.outstandingSftpRuns())
        .anySatisfy(r -> assertThat(r.failure().type()).isEqualTo("RUN_INTERRUPTED"));
  }

  @Test
  void aMetadataQualificationCannotHideALaterProcessingFailure() {
    UUID raw =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-sale-items",
            FetchMethod.FILE_EXPORT,
            "text/csv",
            new byte[] {5},
            "UTF-8",
            "lightspeed-sale-items");
    ingestion.recordQualification(raw, "SALE_ITEM_METADATA_UNOBSERVED", "Receipt numbers absent");
    ingestion.recordStage(raw, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
    assertThat(ingestion.latestRunPerConnector())
        .anySatisfy(
            r -> {
              assertThat(r.connectorName()).isEqualTo("lightspeed-sale-items");
              assertThat(r.failure().type()).isEqualTo("PROCESSING_CANONICALIZED_FAILED");
            });
  }

  private UUID capture(String ref, byte[] bytes) {
    return ingestion.ingestPush(
        "CTB",
        "ctb-invoices",
        FetchMethod.FILE_EXPORT,
        "text/csv",
        bytes,
        "UTF-8",
        "ctb-invoices",
        ref);
  }
}
