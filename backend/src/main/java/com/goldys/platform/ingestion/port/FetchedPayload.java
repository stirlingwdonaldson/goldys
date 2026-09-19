package com.goldys.platform.ingestion.port;

import com.goldys.platform.ingestion.FetchMethod;
import java.util.Objects;

/**
 * One payload handed over by a connector, exactly as the source delivered it.
 *
 * <p>Vendor DTOs never cross this boundary: an adapter converts whatever it received into bytes
 * plus the metadata needed to interpret them later. The array is copied on the way in and on the
 * way out so neither side can mutate stored evidence.
 */
public record FetchedPayload(
    FetchMethod fetchMethod,
    String contentType,
    byte[] bytes,
    String characterEncoding,
    String fetcherIdentity) {

  public FetchedPayload {
    Objects.requireNonNull(fetchMethod, "fetchMethod");
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(fetcherIdentity, "fetcherIdentity");
    bytes = Objects.requireNonNull(bytes, "bytes").clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }
}
