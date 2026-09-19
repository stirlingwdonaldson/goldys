package com.goldys.platform.ingestion.port;

import java.util.Objects;

/**
 * An expected, classified connector failure — bad credentials, a changed page, a rejected request.
 *
 * <p>Public because adapters live in their own packages and must be able to report failures without
 * inventing a private convention. The message is operator-facing: it must never contain payload
 * contents, credentials, or tokens.
 */
public class ConnectorFetchException extends RuntimeException {
  private final String failureType;

  public ConnectorFetchException(String failureType, String message) {
    super(message);
    this.failureType = Objects.requireNonNull(failureType, "failureType");
  }

  public ConnectorFetchException(String failureType, String message, Throwable cause) {
    super(message, cause);
    this.failureType = Objects.requireNonNull(failureType, "failureType");
  }

  public String failureType() {
    return failureType;
  }
}
