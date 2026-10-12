package com.goldys.platform.forecasting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the self-hosted TimesFM inference service, bound from {@code
 * forecasting.timesfm.*} in {@code application.yml} (defaults live there). Auto-bound by the app's
 * {@code @ConfigurationPropertiesScan}.
 *
 * @param enabled whether the forecasting feature is switched on
 * @param baseUrl the base URL of the TimesFM inference service (e.g. {@code http://localhost:8001})
 */
@ConfigurationProperties(prefix = "forecasting.timesfm")
public record TimesFmProperties(boolean enabled, String baseUrl) {}
