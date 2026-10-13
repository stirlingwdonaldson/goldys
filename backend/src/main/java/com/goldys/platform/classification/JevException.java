package com.goldys.platform.classification;

/**
 * An expected, classified failure from the Jev classification service — an unreachable endpoint, a
 * non-2xx response, or an unparseable body.
 *
 * <p>The message is operator-facing: it must never contain the request payload, credentials, or
 * tokens.
 */
public class JevException extends RuntimeException {

  public JevException(String message) {
    super(message);
  }

  public JevException(String message, Throwable cause) {
    super(message, cause);
  }
}
