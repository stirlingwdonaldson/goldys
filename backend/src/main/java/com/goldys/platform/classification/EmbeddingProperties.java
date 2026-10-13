package com.goldys.platform.classification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the self-hosted embedding service, bound from {@code
 * classification.embedding.*} in {@code application.yml} (defaults live there). Auto-bound by the
 * app's {@code @ConfigurationPropertiesScan}.
 *
 * @param enabled whether the classification feature is switched on
 * @param baseUrl the base URL of the embedding service (e.g. {@code http://localhost:8002})
 */
@ConfigurationProperties(prefix = "classification.embedding")
public record EmbeddingProperties(boolean enabled, String baseUrl) {}
