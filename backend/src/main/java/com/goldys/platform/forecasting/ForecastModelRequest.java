package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The request payload the backend sends to the self-hosted forecasting runtime: the dated internal
 * daily series plus the horizon and the quantile levels to request.
 *
 * <p>The daily series are aligned by index with {@link #dates()}. {@link #net()} is the required
 * target (net revenue, ex GST); the other series ({@code bookedCovers}, {@code bookings}, {@code
 * food}, {@code bev}) are optional covariates and may be {@code null} (the whole series absent) or
 * carry {@code null} elements for missing days. A missing day is a gap, never an implicit zero —
 * the model must be told the difference.
 *
 * <p>The wire contract serialises {@link #bookedCovers()} as {@code booked_covers} — see {@link
 * ForecastModelHttpClient}.
 */
public record ForecastModelRequest(
    List<LocalDate> dates,
    List<BigDecimal> net,
    List<BigDecimal> bookedCovers,
    List<BigDecimal> bookings,
    List<BigDecimal> food,
    List<BigDecimal> bev,
    int horizon,
    List<String> quantiles) {
  public ForecastModelRequest {
    dates = java.util.List.copyOf(dates);
    net = freeze(net);
    bookedCovers = freeze(bookedCovers);
    bookings = freeze(bookings);
    food = freeze(food);
    bev = freeze(bev);
    quantiles = java.util.List.copyOf(quantiles);
  }

  private static List<BigDecimal> freeze(List<BigDecimal> values) {
    return values == null
        ? null
        : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
  }
}
