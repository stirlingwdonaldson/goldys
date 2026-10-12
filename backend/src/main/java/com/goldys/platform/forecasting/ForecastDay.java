package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * One forecasted day: the target date plus the model's quantile estimates, keyed by decile string
 * ({@code "0.1"} … {@code "0.9"}). Persisted inside {@link ForecastRun#series()} as JSONB.
 */
public record ForecastDay(LocalDate date, Map<String, BigDecimal> quantiles) {
  public ForecastDay {
    Objects.requireNonNull(date, "date");
    quantiles = quantiles == null ? Map.of() : Map.copyOf(quantiles);
  }
}
