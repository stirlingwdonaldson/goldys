package com.goldys.platform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goldys.platform.support.PostgresContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class IngestionServicesTest {
  /** Published SHA-256 test vector for the ASCII string "abc". */
  private static final String ABC_SHA_256 =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Autowired IngestionRunService runs;
  @Autowired RawPayloadService payloads;
  @Autowired IngestionRunRepository runRepository;
  @Autowired RawRecordRepository rawRecords;
  @Autowired IngestionFailureRepository failures;

  @Test
  void arbitraryBytesSurviveAndReceiveADigest() {
    UUID runId = runs.start("CTB", "ctb-export", null, Instant.now());
    byte[] bytes = {(byte) 0x50, (byte) 0x4b, (byte) 0xff};

    UUID recordId =
        payloads.persist(
            runId,
            "CTB",
            FetchMethod.FILE_EXPORT,
            "application/octet-stream",
            bytes,
            null,
            "fixture");
    bytes[0] = 0;

    RawRecord saved = rawRecords.findById(recordId).orElseThrow();
    assertThat(saved.payloadBytes()).isEqualTo(new byte[] {(byte) 0x50, (byte) 0x4b, (byte) 0xff});
    assertThat(saved.payloadByteLength()).isEqualTo(3);
    assertThat(saved.payloadSha256()).hasSize(64);
    assertThat(saved.ingestionRunId()).isEqualTo(runId);
  }

  @Test
  void invalidUtf8RoundTripsByteForByte() {
    UUID runId = runs.start("LIGHTSPEED", "lightspeed-backoffice", null, Instant.now());
    byte[] notUtf8 = {(byte) 0xc3, (byte) 0x28, (byte) 0x00, (byte) 0xfe};

    UUID recordId =
        payloads.persist(
            runId, "LIGHTSPEED", FetchMethod.SCRAPE, "text/html", notUtf8, "UTF-8", "fixture");

    assertThat(rawRecords.findById(recordId).orElseThrow().payloadBytes()).isEqualTo(notUtf8);
  }

  @Test
  void digestMatchesThePublishedSha256Vector() {
    UUID runId = runs.start("CTB", "ctb-export", null, Instant.now());

    UUID recordId =
        payloads.persist(
            runId,
            "CTB",
            FetchMethod.FILE_EXPORT,
            "text/plain",
            "abc".getBytes(StandardCharsets.UTF_8),
            "UTF-8",
            "fixture");

    assertThat(rawRecords.findById(recordId).orElseThrow().payloadSha256()).isEqualTo(ABC_SHA_256);
  }

  @Test
  void zeroPayloadsWithAFailureCompletesAsFailed() {
    UUID runId = runs.start("DEPUTY", "deputy-api", null, Instant.now());

    runs.recordFailure(runId, "AUTH_FAILED", "OAuth token rejected", Instant.now());
    runs.complete(runId, null, Instant.now());

    assertThat(runRepository.findById(runId).orElseThrow().status())
        .isEqualTo(IngestionStatus.FAILED);
    assertThat(failures.findByIngestionRunIdOrderByOccurredAtAsc(runId))
        .extracting(IngestionFailure::failureType)
        .containsExactly("AUTH_FAILED");
  }

  @Test
  void payloadsPlusAFailureCompleteAsPartial() {
    UUID runId = runs.start("LIGHTSPEED", "lightspeed-backoffice", null, Instant.now());
    payloads.persist(
        runId, "LIGHTSPEED", FetchMethod.SCRAPE, "text/html", new byte[] {1}, "UTF-8", "fixture");

    runs.recordFailure(runId, "SCHEMA_MISMATCH", "second page changed", Instant.now());
    runs.complete(runId, "page-2", Instant.now());

    assertThat(runRepository.findById(runId).orElseThrow().status())
        .isEqualTo(IngestionStatus.PARTIAL);
  }

  @Test
  void noPayloadsAndNoFailuresCompleteAsNoNewData() {
    UUID runId = runs.start("OPENTABLE", "opentable-browser", "week-1", Instant.now());

    runs.complete(runId, "week-1", Instant.now());

    assertThat(runRepository.findById(runId).orElseThrow().status())
        .isEqualTo(IngestionStatus.NO_NEW_DATA);
  }

  @Test
  void payloadsWithoutFailuresCompleteAsSuccess() {
    UUID runId = runs.start("CTB", "ctb-export", null, Instant.now());
    runs.recordFetched(runId);
    payloads.persist(
        runId, "CTB", FetchMethod.FILE_EXPORT, "text/csv", new byte[] {1, 2}, "UTF-8", "fixture");

    runs.complete(runId, "2026-09-19", Instant.now());

    IngestionRun run = runRepository.findById(runId).orElseThrow();
    assertThat(run.status()).isEqualTo(IngestionStatus.SUCCESS);
    assertThat(run.fetchedCount()).isEqualTo(1);
    assertThat(run.persistedCount()).isEqualTo(1);
    assertThat(run.outputWatermark()).isEqualTo("2026-09-19");
  }

  @Test
  void payloadsCannotBeAddedToACompletedRun() {
    UUID runId = runs.start("CTB", "ctb-export", null, Instant.now());
    runs.complete(runId, null, Instant.now());

    assertThatThrownBy(
            () ->
                payloads.persist(
                    runId,
                    "CTB",
                    FetchMethod.FILE_EXPORT,
                    "text/csv",
                    new byte[] {1},
                    "UTF-8",
                    "fixture"))
        .isInstanceOf(IllegalStateException.class);
  }
}
