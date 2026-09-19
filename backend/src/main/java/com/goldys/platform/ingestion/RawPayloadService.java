package com.goldys.platform.ingestion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores source bytes before anything tries to understand them.
 *
 * <p>Each payload commits in its own transaction so evidence accepted before a later connector
 * failure survives; the digest is computed over the same copy that is stored, so the two can never
 * describe different bytes.
 */
@Service
class RawPayloadService {
  private static final Clock CLOCK = Clock.systemUTC();

  private final IngestionRunRepository runs;
  private final RawRecordRepository rawRecords;

  RawPayloadService(IngestionRunRepository runs, RawRecordRepository rawRecords) {
    this.runs = runs;
    this.rawRecords = rawRecords;
  }

  @Transactional
  UUID persist(
      UUID ingestionRunId,
      String sourceSystem,
      FetchMethod fetchMethod,
      String contentType,
      byte[] payloadBytes,
      String characterEncoding,
      String fetcherIdentity) {
    Objects.requireNonNull(payloadBytes, "payloadBytes");
    IngestionRun run =
        runs.findById(ingestionRunId)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown ingestion run " + ingestionRunId));

    // Rejects a completed run before any evidence is written.
    run.recordPersisted();

    byte[] storedBytes = payloadBytes.clone();
    RawRecord record =
        RawRecord.create(
            ingestionRunId,
            sourceSystem,
            fetchMethod,
            contentType,
            storedBytes,
            sha256Hex(storedBytes),
            characterEncoding,
            fetcherIdentity,
            CLOCK.instant());

    UUID recordId = rawRecords.save(record).id();
    runs.save(run);
    return recordId;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }
}
