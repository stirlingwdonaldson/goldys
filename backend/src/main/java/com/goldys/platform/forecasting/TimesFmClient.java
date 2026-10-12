package com.goldys.platform.forecasting;

/**
 * The port through which the platform calls the self-hosted TimesFM inference service. Adapters are
 * swappable behind this interface (the JDK HTTP adapter is {@link TimesFmHttpClient}).
 */
public interface TimesFmClient {

  /** Forecast the target series ahead of its last observed date. */
  TimesFmForecast forecast(TimesFmRequest request);
}
