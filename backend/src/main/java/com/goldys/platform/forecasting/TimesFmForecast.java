package com.goldys.platform.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The TimesFM inference service's response: the future dates forecast, and for each a quantile
 * series keyed by decile string ({@code "0.1"} … {@code "0.9"}). Each quantile list is aligned by
 * index with {@link #dates()}.
 */
public record TimesFmForecast(List<LocalDate> dates, Map<String, List<BigDecimal>> quantiles) {}
