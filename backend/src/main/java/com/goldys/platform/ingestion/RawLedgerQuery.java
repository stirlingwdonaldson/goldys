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

  /** A stored raw payload: its record id and a defensive copy of its bytes. */
  public record RawPayload(UUID id, byte[] bytes) {}
}
