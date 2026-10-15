package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One forecasted day: the target date, the model's quantile estimates (keyed by decile string
 * {@code "0.1"} … {@code "0.9"}), and the richer per-day context the forecast service returns — the
 * food/bev split, the seasonal-naive baseline, the future booked covers, and the external driver
 * flags (holidays, school/university, weather). Persisted inside {@link ForecastRun#series()} as
 * JSONB.
 *
 * <p>Quantile values may be {@code null}: the Python service serialises NaN as JSON {@code null},
 * and a missing decile is a legitimate "no estimate" rather than a failed run. The copy is
 * therefore null-tolerant (unlike {@link Map#copyOf(Map)}, which rejects null values).
 *
 * <p>Every field beyond {@code date}/{@code quantiles} is nullable: a driver is absent when its
 * source was unavailable (an external API outage degrades to {@code null}, never a crash) or when
 * the XGBoost twin could not produce a share/baseline.
 */
public record ForecastDay(
    LocalDate date,
    Map<String, BigDecimal> quantiles,
    BigDecimal food,
    BigDecimal bev,
    BigDecimal baseline,
    BigDecimal bookedCovers,
    Boolean publicHoliday,
    String publicHolidayName,
    Boolean publicHolidayEve,
    Boolean dayAfterPublicHoliday,
    Boolean schoolHoliday,
    Boolean uniInSession,
    BigDecimal serviceTempMaxC,
    BigDecimal serviceRainMm,
    String weatherSource) {

  public ForecastDay {
    Objects.requireNonNull(date, "date");
    quantiles =
        quantiles == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
  }

  /** A day with only the quantiles (pre-diagnostics callers and tests). */
  public ForecastDay(LocalDate date, Map<String, BigDecimal> quantiles) {
    this(
        date, quantiles, null, null, null, null, null, null, null, null, null, null, null, null,
        null);
  }
}
