package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionModelTest {
  private static final String DIGEST = "a".repeat(64);

  @Test
  void successfulRunRecordsCountsAndCompletion() {
    var run =
        IngestionRun.start(
            "LIGHTSPEED",
            "lightspeed-backoffice",
            "cursor-1",
            Instant.parse("2026-09-19T10:00:00Z"));

    run.recordFetched();
    run.recordPersisted();
    run.complete(IngestionStatus.SUCCESS, "cursor-2", null, Instant.parse("2026-09-19T10:01:00Z"));

    assertThat(run.status()).isEqualTo(IngestionStatus.SUCCESS);
    assertThat(run.fetchedCount()).isEqualTo(1);
    assertThat(run.persistedCount()).isEqualTo(1);
    assertThat(run.outputWatermark()).isEqualTo("cursor-2");
    assertThat(run.completedAt()).isEqualTo(Instant.parse("2026-09-19T10:01:00Z"));
  }

  @Test
  void completedRunCannotCompleteAgain() {
    var run = IngestionRun.start("CTB", "ctb-export", null, Instant.now());
    run.complete(IngestionStatus.NO_NEW_DATA, null, null, Instant.now());

    assertThatThrownBy(() -> run.complete(IngestionStatus.SUCCESS, null, null, Instant.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runCannotCompleteIntoTheRunningState() {
    var run = IngestionRun.start("CTB", "ctb-export", null, Instant.now());

    assertThatThrownBy(() -> run.complete(IngestionStatus.RUNNING, null, null, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completedRunNoLongerCountsWork() {
    var run = IngestionRun.start("CTB", "ctb-export", null, Instant.now());
    run.complete(IngestionStatus.NO_NEW_DATA, null, null, Instant.now());

    assertThatThrownBy(run::recordFetched).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(run::recordPersisted).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rawRecordDefensivelyCopiesBytes() {
    UUID runId = UUID.randomUUID();
    byte[] input = {1, 2, 3};

    RawRecord record =
        RawRecord.create(
            runId,
            "CTB",
            FetchMethod.FILE_EXPORT,
            "application/octet-stream",
            input,
            DIGEST,
            null,
            "fixture",
            Instant.now());

    input[0] = 9;
    byte[] output = record.payloadBytes();
    output[1] = 9;

    assertThat(record.payloadBytes()).containsExactly(1, 2, 3);
    assertThat(record.payloadByteLength()).isEqualTo(3);
    assertThat(record.ingestionRunId()).isEqualTo(runId);
  }

  @Test
  void rawRecordRequiresALowerCaseSha256Digest() {
    assertThatThrownBy(
            () ->
                RawRecord.create(
                    UUID.randomUUID(),
                    "CTB",
                    FetchMethod.FILE_EXPORT,
                    "application/octet-stream",
                    new byte[] {1},
                    "A".repeat(64),
                    null,
                    "fixture",
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
