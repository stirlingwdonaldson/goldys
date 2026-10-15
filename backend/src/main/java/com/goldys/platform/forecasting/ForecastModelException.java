package com.goldys.platform.forecasting;

/**
 * An expected, classified failure from the self-hosted forecasting runtime — an unreachable
 * endpoint, a non-2xx response, or an unparseable body.
 *
 * <p>The message is operator-facing: it must never contain the request payload, credentials, or
 * tokens.
 */
public class ForecastModelException extends RuntimeException {

  public ForecastModelException(String message) {
    super(message);
  }

  public ForecastModelException(String message, Throwable cause) {
    super(message, cause);
  }
}
