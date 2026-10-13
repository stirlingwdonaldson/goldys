package com.goldys.platform.classification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the hosted Jev classification service, bound from {@code classification.jev.*}
 * in {@code application.yml} (defaults live there). Auto-bound by the app's
 * {@code @ConfigurationPropertiesScan}.
 *
 * @param enabled whether the Jev classifier is switched on
 * @param baseUrl the base URL of the Jev service (e.g. {@code https://api.typesafe.ai})
 * @param apiKey the Jev API key; empty when unset
 */
@ConfigurationProperties(prefix = "classification.jev")
public record JevProperties(boolean enabled, String baseUrl, String apiKey) {}
