package com.goldys.platform.forecasting;

/**
 * The port through which the platform calls the self-hosted forecasting runtime (TimesFM + the
 * diagnostic XGBoost twin). Adapters are swappable behind this interface (the JDK HTTP adapter is
 * {@link ForecastModelHttpClient}).
 */
public interface ForecastModelClient {

  /** Forecast the target series ahead of its last observed date. */
  ForecastModelResponse forecast(ForecastModelRequest request);
}
