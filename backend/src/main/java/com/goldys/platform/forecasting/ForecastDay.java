package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One forecasted day: the target date plus the model's quantile estimates, keyed by decile string
 * ({@code "0.1"} … {@code "0.9"}). Persisted inside {@link ForecastRun#series()} as JSONB.
 *
 * <p>Quantile values may be {@code null}: the Python service serialises NaN as JSON {@code null},
 * and a missing decile is a legitimate "no estimate" rather than a failed run. The copy is
 * therefore null-tolerant (unlike {@link Map#copyOf(Map)}, which rejects null values).
 */
public record ForecastDay(LocalDate date, Map<String, BigDecimal> quantiles) {
  public ForecastDay {
    Objects.requireNonNull(date, "date");
    quantiles =
        quantiles == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
  }
}
