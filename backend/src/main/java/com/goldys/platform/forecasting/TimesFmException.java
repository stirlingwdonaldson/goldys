package com.goldys.platform.forecasting;

/**
 * An expected, classified failure from the TimesFM inference service — an unreachable endpoint, a
 * non-2xx response, or an unparseable body.
 *
 * <p>The message is operator-facing: it must never contain the request payload, credentials, or
 * tokens.
 */
public class TimesFmException extends RuntimeException {

  public TimesFmException(String message) {
    super(message);
  }

  public TimesFmException(String message, Throwable cause) {
    super(message, cause);
  }
}
