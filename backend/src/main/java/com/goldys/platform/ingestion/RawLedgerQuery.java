package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read-only access to the raw ingestion ledger, for backfill/replay. */
@Service
public class RawLedgerQuery {
  private final RawRecordRepository rawRecords;

  public RawLedgerQuery(RawRecordRepository rawRecords) {
    this.rawRecords = rawRecords;
  }

  public List<RawPayload> rawPayloadsForSource(String sourceSystem) {
    return rawRecords.findBySourceSystem(sourceSystem).stream()
        .map(r -> new RawPayload(r.id(), r.payloadBytes()))
        .toList();
  }

  /** Stored payloads of one source and content type (e.g. CTB invoice PDFs), for replay. */
  public List<RawPayload> rawPayloadsForSource(String sourceSystem, String contentType) {
    return rawRecords.findBySourceSystemAndContentType(sourceSystem, contentType).stream()
        .map(r -> new RawPayload(r.id(), r.payloadBytes()))
        .toList();
  }

  /** Raw-record ids of one fetcher identity (dataset key), for replay/backfill. */
  public List<UUID> rawRecordIdsForFetcher(String fetcherIdentity) {
    return rawRecords.findIdsByFetcherIdentity(fetcherIdentity);
  }

  /** The stored bytes of one raw record (a defensive copy), for replay. */
  public byte[] payloadBytes(UUID id) {
    return rawRecords.findById(id).map(RawRecord::payloadBytes).orElse(null);
  }

  /** Stored payloads of one source and fetcher identity (e.g. the batched recipe-cost page). */
  public List<RawPayload> rawPayloadsForFetcher(String sourceSystem, String fetcherIdentity) {
    return rawRecords.findBySourceSystemAndFetcherIdentity(sourceSystem, fetcherIdentity).stream()
        .map(r -> new RawPayload(r.id(), r.payloadBytes()))
        .toList();
  }

  /**
   * Stored payloads of one source and fetcher identity, with each record's {@code
   * source_record_ref} (e.g. {@code "recipe:617"} for the per-recipe ingredient payloads), for
   * replay that must key on the record's identity rather than bytes alone.
   */
  public List<RawPayloadWithRef> rawPayloadsForFetcherWithRef(
      String sourceSystem, String fetcherIdentity) {
    return rawRecords.findBySourceSystemAndFetcherIdentity(sourceSystem, fetcherIdentity).stream()
        .map(r -> new RawPayloadWithRef(r.id(), r.payloadBytes(), r.sourceRecordRef()))
        .toList();
  }

  /**
   * Stored payloads of one source and fetcher identity with each record's {@code fetched_at},
   * ascending. Used by replays of re-pulled datasets (e.g. the daily stocktake header list) that
   * must keep the most recent version per id rather than the first.
   */
  public List<RawPayloadWithRefAndTime> rawPayloadsForFetcherWithFetchedAt(
      String sourceSystem, String fetcherIdentity) {
    return rawRecords
        .findBySourceSystemAndFetcherIdentityOrderByFetchedAtAsc(sourceSystem, fetcherIdentity)
        .stream()
        .map(
            r ->
                new RawPayloadWithRefAndTime(
                    r.id(), r.payloadBytes(), r.sourceRecordRef(), r.fetchedAt()))
        .toList();
  }

  /**
   * Lightweight references (id, source-record ref, fetch time) of one source and fetcher identity,
   * ascending by {@code fetched_at} then id, without loading any {@code payload_bytes}. Used by the
   * source-product-sale-fact replay to stream bytes one record at a time.
   */
  public List<RawRecordRef> rawRecordRefsForFetcher(String sourceSystem, String fetcherIdentity) {
    return rawRecords
        .findRefsBySourceSystemAndFetcherIdentity(sourceSystem, fetcherIdentity)
        .stream()
        .map(r -> new RawRecordRef(r.getId(), r.getSourceRecordRef(), r.getFetchedAt()))
        .toList();
  }

  /**
   * The latest stored payload's id and SHA-256 for one dataset window ({@code source_record_ref}),
   * without loading its bytes; empty when that window was never stored. Lets a connector skip
   * re-storing a byte-identical response.
   */
  public Optional<StoredDigest> latestDigest(
      String sourceSystem, String fetcherIdentity, String sourceRecordRef) {
    return rawRecords
        .findFirstBySourceSystemAndFetcherIdentityAndSourceRecordRefOrderByFetchedAtDesc(
            sourceSystem, fetcherIdentity, sourceRecordRef)
        .map(d -> new StoredDigest(d.getId(), d.getPayloadSha256()));
  }

  /** A stored raw payload's id and lowercase hex SHA-256. */
  public record StoredDigest(UUID id, String sha256) {}

  /** A stored raw payload: its record id and a defensive copy of its bytes. */
  public record RawPayload(UUID id, byte[] bytes) {}

  /** A stored raw payload plus its source-record reference. */
  public record RawPayloadWithRef(UUID id, byte[] bytes, String sourceRecordRef) {}

  /** A stored raw payload plus its source-record reference and fetch time. */
  public record RawPayloadWithRefAndTime(
      UUID id, byte[] bytes, String sourceRecordRef, Instant fetchedAt) {}

  /**
   * A lightweight raw-record reference: id, source-record ref and fetch time, without its bytes.
   */
  public record RawRecordRef(UUID id, String sourceRecordRef, Instant fetchedAt) {}
}
