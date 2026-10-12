package com.goldys.platform.application;

import java.time.LocalDate;
import java.util.List;

/**
 * The body of {@code POST /api/forecasting/runs}: the target metric to forecast, the past-only
 * covariate metrics, and the history window plus horizon. Covariates are validated (and
 * permission-checked) by {@link ForecastingApplicationService}, never trusted as-is.
 */
public record CreateForecastRequest(
    String target, List<String> covariates, LocalDate historyFrom, int horizonDays) {}
