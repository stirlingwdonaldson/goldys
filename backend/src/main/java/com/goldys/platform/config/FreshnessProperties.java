package com.goldys.platform.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-domain freshness thresholds used to decide whether a resolved figure is still {@code FRESH}
 * or has gone {@code STALE}. Keyed by the source domain of the metric (see the plan's domain names
 * such as {@code resolved_daily_sales} and {@code accounting}), each value is how old the domain's
 * last successful ingestion may be before its data is considered stale.
 *
 * <p>Bound from {@code app.freshness.thresholds} in {@code application.yml}. The thresholds are
 * provisional and deliberately centralised here so they can be tuned per environment without a
 * rebuild.
 */
@ConfigurationProperties(prefix = "app.freshness")
public record FreshnessProperties(Map<String, Duration> thresholds) {

  public FreshnessProperties {
    thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
  }
}
