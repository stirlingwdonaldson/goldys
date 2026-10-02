package com.goldys.platform.ingestion;

import java.time.Instant;
import java.util.Objects;

/** The latest failure of one ingestion run, exposed to the connector API. */
public record FailureDetail(String type, String message, Instant at, String stackTrace) {
  public FailureDetail {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(at, "at");
  }
}
