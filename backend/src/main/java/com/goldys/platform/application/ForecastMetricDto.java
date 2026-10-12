package com.goldys.platform.application;

/** One forecastable metric's catalogue metadata for the "create a forecast" picker. */
public record ForecastMetricDto(
    String metric, String name, String unit, String sourceDomain, String requiredPermission) {}
