package com.goldys.platform.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-domain freshness configuration: the thresholds used to decide whether a resolved figure is
 * still {@code FRESH} or has gone {@code STALE}, and the source systems each domain draws from.
 *
 * <p>Keyed by the source domain of the metric (see the plan's domain names such as {@code
 * resolved_daily_sales} and {@code accounting}), each threshold is how old the domain's last
 * successful ingestion may be before its data is considered stale. {@code sources} lists the
 * connector source-system names (e.g. {@code LIGHTSPEED}, {@code CTB}) that back a domain, so
 * freshness can be derived from the domain's actual connectors rather than a single resolved
 * source.
 *
 * <p>Bound from {@code app.freshness.thresholds} and {@code app.freshness.sources} in {@code
 * application.yml}. The values are provisional and deliberately centralised here so they can be
 * tuned per environment without a rebuild.
 */
@ConfigurationProperties(prefix = "app.freshness")
public record FreshnessProperties(
    Map<String, Duration> thresholds, Map<String, List<String>> sources) {

  public FreshnessProperties {
    thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    sources =
        sources == null
            ? Map.of()
            : sources.entrySet().stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }
}
