package com.goldys.platform.application;

import java.util.List;
import java.util.UUID;

/**
 * Forecast-versus-actual for a succeeded run. {@code mape}/{@code mae} are {@code null} when no
 * forecast date has a resolved, non-zero actual yet (the run is too recent to judge).
 */
public record ForecastComparisonDto(
    UUID runId, Double mape, Double mae, List<ForecastComparisonPointDto> points) {}
