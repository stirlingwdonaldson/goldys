package com.goldys.platform.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The public shape of a forecast run. {@code series} is populated only once the run has {@code
 * SUCCEEDED}; until then it is {@code null} so a client cannot mistake an empty forecast for a
 * successful one.
 */
public record ForecastRunDto(
    UUID id,
    String status,
    String targetMetric,
    List<String> covariates,
    LocalDate historyFrom,
    LocalDate historyTo,
    int horizonDays,
    Instant createdAt,
    List<ForecastSeriesPointDto> series,
    String errorMessage,
    String modelVersion) {}
