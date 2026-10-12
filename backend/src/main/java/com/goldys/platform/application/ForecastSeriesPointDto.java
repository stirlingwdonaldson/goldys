package com.goldys.platform.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One forecasted day: the median plus the {@code p10}/{@code p90} deciles, ready to chart. */
public record ForecastSeriesPointDto(
    LocalDate date, BigDecimal median, BigDecimal p10, BigDecimal p90) {}
