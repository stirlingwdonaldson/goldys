package com.goldys.platform.connectors.opentable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The latest OpenTable payload already in the raw ledger for one dataset window, so the connector
 * can skip re-storing a response whose content hasn't changed. Read-only; backed by {@code
 * RawLedgerQuery} in production and a map in tests.
 */
@FunctionalInterface
public interface StoredPayloads {
  Optional<Stored> latest(String fetcherIdentity, String sourceRecordRef);

  /**
   * One stored payload: its raw-record id, the SHA-256 of its exact bytes, and a lazy loader for
   * those bytes (only needed when the exact hashes differ and the content must be compared).
   */
  record Stored(UUID rawRecordId, String sha256, Supplier<byte[]> bytes) {}

  /** No history: every response is stored. */
  StoredPayloads NONE = (fetcherIdentity, sourceRecordRef) -> Optional.empty();
}
