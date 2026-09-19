package com.goldys.platform.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One source observation, stored exactly as the source delivered it.
 *
 * <p>The bytes plus their digest are the audit evidence, so every source fact column is mapped
 * non-updatable and the byte array is copied on the way in and on the way out. The database
 * additionally rejects UPDATE and DELETE (see {@code V2__ingestion_ledger.sql}); this class keeps
 * application code from attempting a mutation in the first place.
 */
@Entity
@Table(name = "raw_record")
class RawRecord {
  private static final Pattern LOWER_CASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

  @Id private UUID id;

  @Column(name = "ingestion_run_id", nullable = false, updatable = false)
  private UUID ingestionRunId;

  @Column(name = "source_system", nullable = false, updatable = false)
  private String sourceSystem;

  @Enumerated(EnumType.STRING)
  @Column(name = "fetch_method", nullable = false, updatable = false)
  private FetchMethod fetchMethod;

  @Column(name = "content_type", nullable = false, updatable = false)
  private String contentType;

  @Column(name = "payload_bytes", nullable = false, updatable = false)
  private byte[] payloadBytes;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "payload_sha256", nullable = false, updatable = false, length = 64)
  private String payloadSha256;

  @Column(name = "payload_byte_length", nullable = false, updatable = false)
  private long payloadByteLength;

  @Column(name = "character_encoding", updatable = false, length = 64)
  private String characterEncoding;

  @Column(name = "fetcher_identity", nullable = false, updatable = false)
  private String fetcherIdentity;

  @Column(name = "fetched_at", nullable = false, updatable = false)
  private Instant fetchedAt;

  protected RawRecord() {}

  private RawRecord(
      UUID ingestionRunId,
      String sourceSystem,
      FetchMethod fetchMethod,
      String contentType,
      byte[] payloadBytes,
      String payloadSha256,
      String characterEncoding,
      String fetcherIdentity,
      Instant fetchedAt) {
    this.id = UUID.randomUUID();
    this.ingestionRunId = Objects.requireNonNull(ingestionRunId, "ingestionRunId");
    this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
    this.fetchMethod = Objects.requireNonNull(fetchMethod, "fetchMethod");
    this.contentType = Objects.requireNonNull(contentType, "contentType");
    this.payloadBytes = Objects.requireNonNull(payloadBytes, "payloadBytes").clone();
    this.payloadSha256 = requireLowerCaseSha256(payloadSha256);
    this.payloadByteLength = this.payloadBytes.length;
    this.characterEncoding = characterEncoding;
    this.fetcherIdentity = Objects.requireNonNull(fetcherIdentity, "fetcherIdentity");
    this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
  }

  static RawRecord create(
      UUID ingestionRunId,
      String sourceSystem,
      FetchMethod fetchMethod,
      String contentType,
      byte[] payloadBytes,
      String payloadSha256,
      String characterEncoding,
      String fetcherIdentity,
      Instant fetchedAt) {
    return new RawRecord(
        ingestionRunId,
        sourceSystem,
        fetchMethod,
        contentType,
        payloadBytes,
        payloadSha256,
        characterEncoding,
        fetcherIdentity,
        fetchedAt);
  }

  private static String requireLowerCaseSha256(String digest) {
    Objects.requireNonNull(digest, "payloadSha256");
    if (!LOWER_CASE_SHA_256.matcher(digest).matches()) {
      throw new IllegalArgumentException("payloadSha256 must be 64 lower-case hex characters");
    }
    return digest;
  }

  UUID id() {
    return id;
  }

  UUID ingestionRunId() {
    return ingestionRunId;
  }

  String sourceSystem() {
    return sourceSystem;
  }

  FetchMethod fetchMethod() {
    return fetchMethod;
  }

  String contentType() {
    return contentType;
  }

  byte[] payloadBytes() {
    return payloadBytes.clone();
  }

  String payloadSha256() {
    return payloadSha256;
  }

  long payloadByteLength() {
    return payloadByteLength;
  }

  String characterEncoding() {
    return characterEncoding;
  }

  String fetcherIdentity() {
    return fetcherIdentity;
  }

  Instant fetchedAt() {
    return fetchedAt;
  }
}
