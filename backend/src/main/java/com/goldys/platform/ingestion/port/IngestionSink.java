package com.goldys.platform.ingestion.port;

import java.util.UUID;

/**
 * Where a connector hands each payload as soon as it has it.
 *
 * <p>Streaming rather than returning a collection is deliberate: evidence accepted before a later
 * page fails is already stored, and the run can end PARTIAL instead of losing everything.
 */
@FunctionalInterface
public interface IngestionSink {
  /** Stores the payload and returns the identifier of the stored raw record. */
  UUID accept(FetchedPayload payload);
}
