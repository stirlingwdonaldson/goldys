package com.goldys.platform.ingestion;

import java.util.List;
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

  /** A stored raw payload: its record id and a defensive copy of its bytes. */
  public record RawPayload(UUID id, byte[] bytes) {}

  /** A stored raw payload plus its source-record reference. */
  public record RawPayloadWithRef(UUID id, byte[] bytes, String sourceRecordRef) {}
}
