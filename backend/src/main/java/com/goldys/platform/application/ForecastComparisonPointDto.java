package com.goldys.platform.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One forecast date compared against its resolved actual, where one exists yet. */
public record ForecastComparisonPointDto(
    LocalDate date, BigDecimal predictedMedian, BigDecimal actual) {}
