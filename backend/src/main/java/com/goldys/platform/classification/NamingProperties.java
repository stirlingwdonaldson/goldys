package com.goldys.platform.classification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the LLM category-naming step, bound from {@code classification.naming.*} in
 * {@code application.yml} (defaults live there). Auto-bound by the app's
 * {@code @ConfigurationPropertiesScan}.
 *
 * @param enabled whether the naming step runs after clustering; when false (or no model is
 *     configured) clustering still succeeds with no suggestions
 */
@ConfigurationProperties(prefix = "classification.naming")
public record NamingProperties(boolean enabled) {}
