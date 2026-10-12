package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The past-side payload the backend sends to the self-hosted TimesFM inference service: the dated
 * target history, optional aligned covariates, the number of days to forecast, and the quantile
 * levels to request.
 *
 * <p>The wire contract adds {@code freq:"D"} on top of these fields — see {@link TimesFmHttpClient}
 * — because the frequency is a fixed platform constant (daily grain) rather than a per-request
 * choice.
 */
public record TimesFmRequest(
    List<LocalDate> dates,
    List<BigDecimal> target,
    List<Covariate> covariates,
    int horizonDays,
    List<String> quantiles) {

  /** One covariate series: a name plus a value per {@link #dates()} entry, aligned by index. */
  public record Covariate(String name, List<BigDecimal> values) {}
}
