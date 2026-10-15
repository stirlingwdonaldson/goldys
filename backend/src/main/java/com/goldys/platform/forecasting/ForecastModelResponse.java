package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The forecasting runtime's response: the per-day forecast and the model's diagnostic payload.
 *
 * <p>{@link #diagnostics()} is the raw JSON of the diagnostic object (or {@code null} when the
 * XGBoost twin failed, which never loses the headline forecast). It is carried as a string so the
 * caller can persist it as-is (matching {@link ForecastRun#diagnostics()}) or re-parse it with
 * Jackson. {@link #warnings()} is the top-level warning list the service emits alongside {@code
 * diagnostics} — non-empty on the failure path, empty otherwise.
 *
 * <p>Each {@link Day} maps one-to-one onto the wire's per-day object. The flag fields ({@link
 * Day#publicHoliday()} and the other {@code int} drivers) are {@code 0}/{@code 1} on the wire, with
 * {@code null} meaning the driver is absent; every value field may be {@code null} because the
 * Python service serialises NaN as JSON {@code null}.
 */
public record ForecastModelResponse(List<Day> forecast, String diagnostics, List<String> warnings) {

  /** One forecasted day, matching the wire's per-day object field-for-field. */
  public record Day(
      LocalDate date,
      BigDecimal median,
      BigDecimal p10,
      BigDecimal p90,
      BigDecimal food,
      BigDecimal bev,
      BigDecimal baseline,
      BigDecimal bookedCovers,
      Integer publicHoliday,
      String publicHolidayName,
      Integer publicHolidayEve,
      Integer dayAfterPublicHoliday,
      Integer schoolHoliday,
      Integer uniInSession,
      BigDecimal serviceTempMaxC,
      BigDecimal serviceRainMm,
      String weatherSource) {}
}
